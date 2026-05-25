/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when a submitted key carries more subkeys than the server is willing
/// to process during submission and later verified-UID publication.
public final class TooManySubkeysException extends KeyValidationException {

    public TooManySubkeysException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }

    public TooManySubkeysException(String message) {
        super(message, Optional.empty());
    }
}
