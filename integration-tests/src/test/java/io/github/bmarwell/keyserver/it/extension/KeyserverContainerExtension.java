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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
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
 * <p>Containers are created once per JVM (stored in the root extension context) and shut
 * down automatically when all tests finish, because {@link ContainerHolder} implements
 * {@link Store.CloseableResource}.
 *
 * <p>Usage in a test class:
 * <pre>{@code
 * @ExtendWith(KeyserverContainerExtension.class)
 * class MyIT {
 *     @Test
 *     void someTest() throws Exception {
 *         URI pksBase = KeyserverContainerExtension.pksBaseUri();
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>For database seeding, additionally annotate the test class with {@link DatabaseSeed}.
 */
public class KeyserverContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(KeyserverContainerExtension.class);

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

    /**
     * Shared holder populated once per JVM when the first test class is initialised.
     * Volatile so subsequent test classes see the written reference.
     */
    private static volatile @Nullable ContainerHolder activeHolder;

    // -------------------------------------------------------------------------
    // JUnit 5 callbacks
    // -------------------------------------------------------------------------

    @Override
    public void beforeAll(ExtensionContext context) {
        // Obtain or create the shared holder stored at the root context level so it
        // persists across all test classes in the JVM run.
        ContainerHolder holder = context.getRoot()
                .getStore(NS)
                .getOrComputeIfAbsent(HOLDER_KEY, _key -> startContainers(), ContainerHolder.class);
        activeHolder = holder;

        // Apply @DatabaseSeed if present on the test class.
        context.getTestClass()
                .map(cls -> cls.getAnnotation(DatabaseSeed.class))
                .ifPresent(seed -> applySeed(holder, seed));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        // Truncate tables declared by @DatabaseSeed so the next class starts clean.
        context.getTestClass()
                .map(cls -> cls.getAnnotation(DatabaseSeed.class))
                .ifPresent(seed -> truncateTables(seed));
    }

    // -------------------------------------------------------------------------
    // Static accessors — convenient for test classes
    // -------------------------------------------------------------------------

    /** Returns the base URI for HKP (PKS) endpoints, e.g. {@code http://localhost:9080/pks}. */
    public static URI pksBaseUri() {
        return URI.create(requireHolder().pksBaseUrl());
    }

    /** Returns the base URI for the REST (JSON) endpoints, e.g. {@code http://localhost:9080/api}. */
    public static URI apiBaseUri() {
        return URI.create(requireHolder().apiBaseUrl());
    }

    /** Returns a JDBC URL for the test PostgreSQL instance. */
    public static String jdbcUrl() {
        return requireHolder().postgres().getJdbcUrl();
    }

    /** Returns the DB user name. */
    public static String dbUser() {
        return PG_USER;
    }

    /** Returns the DB password. */
    public static String dbPassword() {
        return PG_PASSWORD;
    }

    // -------------------------------------------------------------------------
    // Container startup
    // -------------------------------------------------------------------------

    @SuppressWarnings("resource") // resources are closed via ContainerHolder.close()
    private static ContainerHolder startContainers() {
        LOG.info("Starting shared keyserver test containers…");

        Network network = Network.newNetwork();

        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
                .withNetwork(network)
                .withNetworkAliases(PG_NETWORK_ALIAS)
                .withDatabaseName(PG_DATABASE)
                .withUsername(PG_USER)
                .withPassword(PG_PASSWORD);

        postgres.start();

        GenericContainer<?> liberty = buildLibertyContainer(network);
        liberty.start();

        int libertyPort = liberty.getMappedPort(9080);
        String libertyHost = liberty.getHost();
        String pksBaseUrl = "http://" + libertyHost + ":" + libertyPort + "/pks";
        String apiBaseUrl = "http://" + libertyHost + ":" + libertyPort + "/api";

        LOG.info("PostgreSQL JDBC URL: {}", postgres.getJdbcUrl());
        LOG.info("Liberty PKS base URL: {}", pksBaseUrl);
        LOG.info("Liberty REST base URL: {}", apiBaseUrl);

        return new ContainerHolder(postgres, liberty, pksBaseUrl, apiBaseUrl);
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
         *   1. Copy server.xml → /config/server.xml
         *   2. Run features.sh  → installs only the features declared in server.xml
         *   3. Copy WARs        → /config/dropins/  (auto-deployed by Liberty)
         *   4. Copy JDBC driver → /config/lib/global/postgresql.jar
         *   5. Run configure.sh → final Liberty image prep step
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
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("liberty")));
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

    private static void truncateTables(DatabaseSeed seed) {
        if (seed.truncateAfter().length == 0) {
            return;
        }
        ContainerHolder holder = requireHolder();
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

    private static ContainerHolder requireHolder() {
        ContainerHolder holder = activeHolder;
        if (holder == null) {
            throw new IllegalStateException("KeyserverContainerExtension has not been initialised. "
                    + "Make sure the test class is annotated with @ExtendWith(KeyserverContainerExtension.class).");
        }
        return holder;
    }

    private static String requireSystemProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required system property '" + key + "' is not set. "
                    + "Run integration tests via: ./mvnw verify -pl integration-tests -am -P run-its");
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // Holder record — closed by JUnit after the last test in the session
    // -------------------------------------------------------------------------

    /**
     * Holds the running containers and their derived URLs.  JUnit automatically calls
     * {@link #close()} after all tests in the root context finish.
     */
    record ContainerHolder(
            PostgreSQLContainer<?> postgres, GenericContainer<?> liberty, String pksBaseUrl, String apiBaseUrl)
            implements Store.CloseableResource {

        @Override
        public void close() {
            LOG.info("Stopping Liberty container…");
            this.liberty.stop();
            LOG.info("Stopping PostgreSQL container…");
            this.postgres.stop();
        }
    }
}
