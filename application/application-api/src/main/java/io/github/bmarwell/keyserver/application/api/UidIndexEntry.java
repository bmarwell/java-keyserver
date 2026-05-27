/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

import java.time.OffsetDateTime;
import java.util.Optional;

/// Metadata for a single verified UID, used in `op=index` responses.
///
/// All timestamps are `Optional` because UID packets do not always carry
/// creation/expiration data (older keys often omit them).
public record UidIndexEntry(
        String uidRaw,
        Optional<OffsetDateTime> creationTime,
        Optional<OffsetDateTime> expirationTime,
        boolean revoked) {}
