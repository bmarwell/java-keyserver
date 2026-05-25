/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when the key uses a public-key algorithm that this server does not accept
/// (e.g. v3 RSA with MD5, DSA below 2048 bits).
public final class InvalidAlgorithmException extends KeyValidationException {

    /// The numeric OpenPGP algorithm tag that was rejected.
    private final int algorithmTag;

    public InvalidAlgorithmException(String message, int algorithmTag, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
        this.algorithmTag = algorithmTag;
    }

    public InvalidAlgorithmException(String message, int algorithmTag) {
        super(message, Optional.empty());
        this.algorithmTag = algorithmTag;
    }

    public int getAlgorithmTag() {
        return algorithmTag;
    }
}
