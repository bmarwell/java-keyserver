/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

import java.net.URI;

/**
 * Immutable snapshot of a running keyserver instance, injected by
 * {@link KeyserverContainerExtension} into test methods.
 *
 * <p>All coordinates (URLs, JDBC URL, credentials) are resolved from the live containers
 * at injection time, so ports are always correct regardless of host mapping.
 *
 * <p>Test code should declare parameters as {@link KeyserverAccess} rather than
 * {@code KeyserverInstance} for better decoupling from the container implementation.
 */
public record KeyserverInstance(URI pksBaseUri, URI apiBaseUri, String jdbcUrl, String dbUser, String dbPassword)
        implements KeyserverAccess {}
