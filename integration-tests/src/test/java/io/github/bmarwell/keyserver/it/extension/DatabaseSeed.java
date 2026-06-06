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

/**
 * Marks a test class that needs a specific database state before tests run.
 *
 * <p>The {@link KeyserverContainerExtension} reads this annotation in {@code beforeAll}.
 * SQL files listed in {@link #value()} are executed in order against the shared
 * PostgreSQL container before any test in the annotated class.
 *
 * <p>After all tests in the class finish, the tables listed in {@link #truncateAfter()}
 * are truncated in a single statement (CASCADE) so the next test class starts clean.
 *
 * <p>Example:
 * <pre>{@code
 * @DatabaseSeed(value = {"sql/some-keys.sql"}, truncateAfter = {"keys", "uids"})
 * class LookupIT {
 *     // tests here can assume that some-keys.sql data is present
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface DatabaseSeed {

    /**
     * Classpath resources containing SQL INSERT/COPY statements to execute before
     * the test class runs.  Paths are resolved relative to the classpath root.
     */
    String[] value() default {};

    /**
     * Table names to TRUNCATE CASCADE after all tests in the annotated class finish.
     * Defaults to the full set of application tables.
     */
    String[] truncateAfter() default {"uids", "keys", "verification_queue", "business_transactions"};
}
