/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when the primary key carries a valid revocation signature.
public final class KeyRevokedException extends KeyValidationException {

    public KeyRevokedException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }
}
