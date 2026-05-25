/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Base for exceptions raised when a key is structurally valid but fails
/// business-level validation rules (expired, revoked, no usable UIDs, …).
public abstract sealed class KeyValidationException extends KeyServerException
        permits KeyExpiredException,
                KeyRevokedException,
                NoVerifiableUidException,
                InvalidAlgorithmException,
                TooManyVerifiableUidsException {

    protected KeyValidationException(String message, Optional<KeyFingerprint> fingerprint) {
        super(message, fingerprint);
    }
}
