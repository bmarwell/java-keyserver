/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.extension;

import java.net.URI;

public record KeyserverInstance(URI pksBaseUri, URI apiBaseUri, String jdbcUrl, String dbUser, String dbPassword)
        implements KeyserverAccess {}
