/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

import java.net.URI;

/**
 * Read-only view of a running keyserver test instance.
 *
 * <p>Implementations are injected by {@link KeyserverContainerExtension} as a
 * {@code ParameterResolver} argument into any {@code @Test}, {@code @BeforeEach},
 * or {@code @BeforeAll} method that declares a parameter of this type.
 *
 * <p>Example:
 * <pre>{@code
 * @KeyserverIntegrationTest
 * class MyIT {
 *
 *     @Test
 *     void lookup(KeyserverAccess keyserver) throws Exception {
 *         URI endpoint = keyserver.pksBaseUri().resolve("/pks/lookup?op=get&search=...");
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>The concrete type injected is {@link KeyserverInstance}.  Test code should
 * declare the parameter as {@code KeyserverAccess} so tests remain decoupled from
 * the container implementation.
 *
 * <p><b>Parallel test classes:</b> all classes that use {@code @KeyserverIntegrationTest}
 * in the same JVM share the same running containers.  Tests that need an isolated
 * database state should annotate their class with {@link DatabaseSeed}.
 */
public interface KeyserverAccess {

    /** Base URI of the HKP (PKS) endpoint, e.g. {@code http://localhost:9080/pks}. */
    URI pksBaseUri();

    /** Base URI of the REST (JSON) endpoint, e.g. {@code http://localhost:9080/api}. */
    URI apiBaseUri();

    /** JDBC URL for direct database access during seed/verify steps. */
    String jdbcUrl();

    /** Database user. */
    String dbUser();

    /** Database password. */
    String dbPassword();
}
