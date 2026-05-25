/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when a lookup finds no key matching the supplied search term
/// (key ID, fingerprint, or email address).
public final class KeyNotFoundException extends KeyServerException {

    public KeyNotFoundException(String message) {
        super(message, Optional.empty());
    }

    public KeyNotFoundException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }
}
