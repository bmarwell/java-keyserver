/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * JUnit 5 extension that starts a shared PostgreSQL container and an Open Liberty container
 * for the duration of the entire test session.
 *
 * <p>Containers are created once per JVM (stored at the root extension-context level) and
 * shut down automatically when all tests finish because {@link ContainerHolder} implements
 * {@link Store.CloseableResource}.
 *
 * <p>The extension implements {@link ParameterResolver}: any {@code @Test},
 * {@code @BeforeEach}, or {@code @BeforeAll} method that declares a {@link KeyserverAccess}
 * (or {@link KeyserverInstance}) parameter will receive the shared running instance.
 *
 * <p><b>Parallel test classes:</b> all classes in the same JVM share one set of containers.
 * This avoids repeated Liberty startups (each takes ~2 minutes). Tests that require a clean
 * database state should annotate their class with {@link DatabaseSeed}; the extension will
 * execute the seed SQL before the class and truncate the declared tables afterwards.
 *
 * <p>This extension is normally activated via the {@link KeyserverIntegrationTest} meta-
 * annotation rather than being referenced directly.
 */
public class KeyserverContainerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(KeyserverContainerExtension.class);
    private static final Logger WEBSPHERE_LIBERTY_LOGGER = LoggerFactory.getLogger("websphere_liberty");

    private static final Namespace NS = Namespace.create(KeyserverContainerExtension.class);
    private static final String HOLDER_KEY = "containers";

    /** Liberty image pulled from IBM Container Registry. */
    private static final String LIBERTY_BASE_IMAGE =
            "icr.io/appcafe/open-liberty:kernel-slim-java25-openj9-ubi-minimal";

    /** Liberty's application server ready log message (CWWKF0011I). */
    private static final String LIBERTY_READY_LOG = ".*CWWKF0011I.*";

    /** Postgres network alias — matches KEYSERVER_DB_SERVER env var fed to Liberty. */
    private static final String PG_NETWORK_ALIAS = "postgres";

    private static final String PG_DATABASE = "keyserver";
    private static final String PG_USER = "keyserver";
    private static final String PG_PASSWORD = "keyserver";

    // -------------------------------------------------------------------------
    // JUnit 5 lifecycle callbacks
    // -------------------------------------------------------------------------

    @Override
    public void beforeAll(ExtensionContext context) {
        // Obtain or create the shared holder at the root context so it is shared
        // across all test classes in the JVM.
        ContainerHolder holder = context.getRoot()
                .getStore(NS)
                .getOrComputeIfAbsent(HOLDER_KEY, _key -> startContainers(), ContainerHolder.class);

        // Apply @DatabaseSeed SQL if present on the test class.
        findSeedAnnotation(context).ifPresent(seed -> applySeed(holder, seed));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Truncate tables declared in @DatabaseSeed so the next class starts clean.
        findSeedAnnotation(context).ifPresent(seed -> truncateTables(requireHolder(context), seed));
    }

    // -------------------------------------------------------------------------
    // ParameterResolver — injects KeyserverAccess / KeyserverInstance into tests
    // -------------------------------------------------------------------------

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == KeyserverAccess.class || type == KeyserverInstance.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return requireHolder(extensionContext).toKeyserverInstance();
    }

    // -------------------------------------------------------------------------
    // Container startup
    // -------------------------------------------------------------------------

    @SuppressWarnings("resource") // resources are closed via ContainerHolder.close()
    private static ContainerHolder startContainers() {
        LOG.info("Starting shared keyserver test containers...");

        // Network is tracked in ContainerHolder so it is closed in close() even if
        // Liberty startup throws after PostgreSQL has already started.
        Network network = Network.newNetwork();
        PostgreSQLContainer<?> postgres = null;
        GenericContainer<?> liberty = null;
        try {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                    .withNetwork(network)
                    .withNetworkAliases(PG_NETWORK_ALIAS)
                    .withDatabaseName(PG_DATABASE)
                    .withUsername(PG_USER)
                    .withPassword(PG_PASSWORD);

            postgres.start();

            liberty = buildLibertyContainer(network);
            liberty.start();
        } catch (RuntimeException ex) {
            // Clean up anything that started before the failure.
            if (liberty != null) {
                liberty.stop();
            }
            if (postgres != null) {
                postgres.stop();
            }
            try {
                network.close();
            } catch (Exception closeEx) {
                ex.addSuppressed(closeEx);
            }
            throw ex;
        }

        int libertyPort = liberty.getMappedPort(9080);
        String libertyHost = liberty.getHost();
        String pksBaseUrl = "http://" + libertyHost + ":" + libertyPort + "/pks";
        String apiBaseUrl = "http://" + libertyHost + ":" + libertyPort + "/api";

        LOG.info("PostgreSQL JDBC URL : {}", postgres.getJdbcUrl());
        LOG.info("Liberty PKS base URL: {}", pksBaseUrl);
        LOG.info("Liberty REST base URL: {}", apiBaseUrl);

        return new ContainerHolder(postgres, liberty, network, pksBaseUrl, apiBaseUrl);
    }

    private static GenericContainer<?> buildLibertyContainer(Network network) {
        String pksWarPath = requireSystemProperty("pks.war.path");
        String restWarPath = requireSystemProperty("rest.war.path");
        String serverXmlPath = requireSystemProperty("liberty.server.xml.path");
        String pgsqlJarPath = requireSystemProperty("pgsql.jar.path");

        /*
         * Build a custom Liberty image using ImageFromDockerfile so we can run
         * features.sh before copying the WARs (required for kernel-slim).
         *
         * Build order:
         *   1. Copy server.xml -> /config/server.xml
         *   2. Run features.sh  -> installs only the features declared in server.xml
         *   3. Copy WARs        -> /config/dropins/  (auto-deployed by Liberty)
         *   4. Copy JDBC driver -> /config/lib/global/postgresql.jar
         *   5. Run configure.sh -> final Liberty image prep step
         */
        String dockerfile = """
        FROM %s
        COPY --chown=1001:0 server.xml /config/server.xml
        RUN features.sh
        COPY --chown=1001:0 pks.war /config/dropins/pks.war
        COPY --chown=1001:0 rest.war /config/dropins/rest.war
        COPY --chown=1001:0 postgresql.jar /config/lib/global/postgresql.jar
        RUN configure.sh
        """.formatted(LIBERTY_BASE_IMAGE);

        ImageFromDockerfile image = new ImageFromDockerfile("keyserver-liberty-it", true)
                .withFileFromString("Dockerfile", dockerfile)
                .withFileFromPath("server.xml", Path.of(serverXmlPath))
                .withFileFromPath("pks.war", Path.of(pksWarPath))
                .withFileFromPath("rest.war", Path.of(restWarPath))
                .withFileFromPath("postgresql.jar", Path.of(pgsqlJarPath));

        return new GenericContainer<>(image)
                .withNetwork(network)
                .withExposedPorts(9080)
                .withEnv("KEYSERVER_DB_SERVER", PG_NETWORK_ALIAS)
                .withEnv("KEYSERVER_DB_NAME", PG_DATABASE)
                .withEnv("KEYSERVER_DB_USER", PG_USER)
                .withEnv("KEYSERVER_DB_PASSWORD", PG_PASSWORD)
                .waitingFor(Wait.forLogMessage(LIBERTY_READY_LOG, 1).withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(WEBSPHERE_LIBERTY_LOGGER));
    }

    // -------------------------------------------------------------------------
    // Database seeding helpers
    // -------------------------------------------------------------------------

    private static void applySeed(ContainerHolder holder, DatabaseSeed seed) {
        if (seed.value().length == 0) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(holder.postgres().getJdbcUrl(), PG_USER, PG_PASSWORD);
                Statement stmt = conn.createStatement()) {
            for (String resource : seed.value()) {
                String sql = readClasspathResource(resource);
                LOG.info("Applying database seed: {}", resource);
                stmt.execute(sql);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to apply @DatabaseSeed", ex);
        }
    }

    private static void truncateTables(ContainerHolder holder, DatabaseSeed seed) {
        if (seed.truncateAfter().length == 0) {
            return;
        }
        String tables = String.join(", ", seed.truncateAfter());
        String sql = "TRUNCATE TABLE " + tables + " CASCADE";
        try (Connection conn = DriverManager.getConnection(holder.postgres().getJdbcUrl(), PG_USER, PG_PASSWORD);
                Statement stmt = conn.createStatement()) {
            LOG.info("Truncating tables after test class: {}", tables);
            stmt.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to truncate tables after @DatabaseSeed class", ex);
        }
    }

    private static String readClasspathResource(String resource) {
        String path = resource.startsWith("/") ? resource : "/" + resource;
        try (InputStream in = KeyserverContainerExtension.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read classpath resource: " + path, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static Optional<DatabaseSeed> findSeedAnnotation(ExtensionContext context) {
        return context.getTestClass().map(cls -> cls.getAnnotation(DatabaseSeed.class));
    }

    private static ContainerHolder requireHolder(ExtensionContext context) {
        return context.getRoot().getStore(NS).get(HOLDER_KEY, ContainerHolder.class);
    }

    private static String requireSystemProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required system property '"
                    + key
                    + "' is not set. "
                    + "Run integration tests via: ./mvnw verify -pl integration-tests -am -P run-its");
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // Holder record — closed by JUnit after the last test in the session
    // -------------------------------------------------------------------------

    /**
     * Holds the running containers and their derived URLs. JUnit automatically calls
     * {@link #close()} after all tests in the root context finish.
     *
     * <p>The {@link Network} is stored here so it can be closed <em>after</em> both
     * containers have stopped. With {@code ryuk.disabled=true} nothing else would
     * remove the dangling network from the Docker/Podman daemon.
     */
    record ContainerHolder(
            PostgreSQLContainer<?> postgres,
            GenericContainer<?> liberty,
            Network network,
            String pksBaseUrl,
            String apiBaseUrl)
            implements Store.CloseableResource {

        KeyserverInstance toKeyserverInstance() {
            return new KeyserverInstance(
                    URI.create(this.pksBaseUrl()),
                    URI.create(this.apiBaseUrl()),
                    this.postgres().getJdbcUrl(),
                    PG_USER,
                    PG_PASSWORD);
        }

        @Override
        public void close() {
            // Best-effort shutdown: every resource is attempted even if a previous step throws.
            // All exceptions are collected; the first is rethrown with the rest as suppressed.
            List<Exception> errors = new ArrayList<>();

            try {
                LOG.info("Stopping Liberty container...");
                this.liberty.stop();
            } catch (Exception ex) {
                LOG.warn("Failed to stop Liberty container", ex);
                errors.add(ex);
            }

            try {
                LOG.info("Stopping PostgreSQL container...");
                this.postgres.stop();
            } catch (Exception ex) {
                LOG.warn("Failed to stop PostgreSQL container", ex);
                errors.add(ex);
            }

            try {
                LOG.info("Removing shared Docker network...");
                this.network.close();
            } catch (Exception ex) {
                LOG.warn("Failed to close Docker network", ex);
                errors.add(ex);
            }

            if (!errors.isEmpty()) {
                RuntimeException first =
                        new RuntimeException("One or more errors during container shutdown", errors.get(0));
                errors.subList(1, errors.size()).forEach(first::addSuppressed);
                throw first;
            }
        }
    }
}
