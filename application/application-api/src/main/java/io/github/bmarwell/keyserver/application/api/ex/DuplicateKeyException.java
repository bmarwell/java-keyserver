/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when the submitted fingerprint is already present in the published keystore.
/// Callers should treat this as idempotent: respond 200 OK and optionally re-send
/// the verification email rather than surfacing it as an error.
public final class DuplicateKeyException extends KeyServerException {

    public DuplicateKeyException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }
}
