/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Composed annotation for keyserver integration tests.
 *
 * <p>Applying {@code @KeyserverIntegrationTest} to a test class is equivalent to:
 * <pre>{@code
 * @Tag("integration")
 * @ExtendWith(KeyserverContainerExtension.class)
 * }</pre>
 *
 * <p>The {@link KeyserverContainerExtension} starts a shared Open Liberty container and
 * a PostgreSQL container (once per JVM) and injects a {@link KeyserverAccess} argument
 * into any test method that declares one.
 *
 * <p>Example:
 * <pre>{@code
 * @KeyserverIntegrationTest
 * class AddKeyIT {
 *
 *     @Test
 *     void add_key_returns_202(KeyserverAccess keyserver) throws Exception {
 *         URI endpoint = keyserver.pksBaseUri().resolve("/pks/add");
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>For database seeding before a test class, additionally annotate with
 * {@link DatabaseSeed}.
 *
 * <p>The {@code "integration"} tag lets CI selectively include or exclude these tests:
 * <pre>{@code
 * ./mvnw verify -pl integration-tests -am -P run-its -Dgroups=integration
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("integration")
@ExtendWith(KeyserverContainerExtension.class)
public @interface KeyserverIntegrationTest {}
