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
import org.jspecify.annotations.Nullable;
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

public class KeyserverContainerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(KeyserverContainerExtension.class);
    private static final Namespace NS = Namespace.create(KeyserverContainerExtension.class);
    private static final String HOLDER_KEY = "containers";

    private static final String PG_NETWORK_ALIAS = "postgres";
    private static final String PG_DATABASE = "keyserver";
    private static final String PG_USER = "keyserver";
    private static final String PG_PASSWORD = "keyserver";

    @Override
    public void beforeAll(ExtensionContext context) {
        ContainerHolder holder = context.getRoot()
                .getStore(NS)
                .getOrComputeIfAbsent(HOLDER_KEY, _key -> startContainers(), ContainerHolder.class);

        findSeedAnnotation(context).ifPresent(seed -> applySeed(holder, seed));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        findSeedAnnotation(context).ifPresent(seed -> truncateTables(requireHolder(context), seed));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == KeyserverAccess.class || type == KeyserverInstance.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return requireHolder(extensionContext).toKeyserverInstance();
    }

    @SuppressWarnings("resource")
    private static ContainerHolder startContainers() {
        LOG.info("Starting shared keyserver test containers...");

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
        String deployablePath = requireSystemProperty("keyserver.deployable.path");
        String serverXmlPath = requireSystemProperty("keyserver.server.xml.path");
        String pgsqlJarPath = requireSystemProperty("keyserver.pgsql.jar.path");

        String deployableName = Path.of(deployablePath).getFileName().toString();
        String dockerfile = """
        FROM %s
        COPY --chown=1001:0 server.xml /config/server.xml
        COPY --chown=1001:0 deployable /config/dropins/%s
        COPY --chown=1001:0 postgresql.jar /config/lib/global/postgresql.jar
        RUN configure.sh
        """.formatted(KeyserverTestImage.LIBERTY_BASE_IMAGE, deployableName);

        ImageFromDockerfile image = new ImageFromDockerfile("keyserver-liberty-it", true)
                .withFileFromString("Dockerfile", dockerfile)
                .withFileFromPath("server.xml", Path.of(serverXmlPath))
                .withFileFromPath("deployable", Path.of(deployablePath))
                .withFileFromPath("postgresql.jar", Path.of(pgsqlJarPath));

        return new GenericContainer<>(image)
                .withNetwork(network)
                .withExposedPorts(9080)
                .withEnv("KEYSERVER_DB_SERVER", PG_NETWORK_ALIAS)
                .withEnv("KEYSERVER_DB_NAME", PG_DATABASE)
                .withEnv("KEYSERVER_DB_USER", PG_USER)
                .withEnv("KEYSERVER_DB_PASSWORD", PG_PASSWORD)
                .waitingFor(Wait.forLogMessage(KeyserverTestImage.LIBERTY_READY_LOG, 1)
                        .withStartupTimeout(Duration.ofMinutes(3)))
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("liberty")));
    }

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

    private static Optional<DatabaseSeed> findSeedAnnotation(ExtensionContext context) {
        return context.getTestClass().map(cls -> cls.getAnnotation(DatabaseSeed.class));
    }

    private static ContainerHolder requireHolder(ExtensionContext context) {
        @Nullable ContainerHolder holder = context.getRoot().getStore(NS).get(HOLDER_KEY, ContainerHolder.class);
        if (holder == null) {
            throw new IllegalStateException("KeyserverContainerExtension containers are not initialised. "
                    + "Ensure beforeAll has run before resolving parameters. "
                    + "Did you use @KeyserverIntegrationTest on the test class?");
        }
        return holder;
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
