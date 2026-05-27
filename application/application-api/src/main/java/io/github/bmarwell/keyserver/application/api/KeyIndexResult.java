/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/// Full index entry for one key, used in `op=index` responses.
///
/// Contains the key metadata fields needed for the HKP machine-readable
/// format (`pub:` line) plus all verified UIDs (`uid:` lines).
/// Only verified UIDs appear in {@code verifiedUids} — unverified UIDs
/// are never exposed.
///
/// The {@code disabled} flag is a keyserver-administrative flag (HKP `d`).
/// It is independent of the OpenPGP {@code revoked} flag: an operator may
/// disable a key on this server without the key owner having revoked it.
public record KeyIndexResult(
        String fingerprint,
        int algorithm,
        Optional<Integer> bitStrength,
        OffsetDateTime creationTime,
        Optional<OffsetDateTime> expirationTime,
        boolean revoked,
        boolean disabled,
        List<UidIndexEntry> verifiedUids) {}
