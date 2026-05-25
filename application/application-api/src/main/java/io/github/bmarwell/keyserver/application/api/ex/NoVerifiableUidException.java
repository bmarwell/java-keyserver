/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when every UID on the submitted key either has no email address,
/// is revoked, or is expired — leaving nothing that can be verified.
public final class NoVerifiableUidException extends KeyValidationException {

    public NoVerifiableUidException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }

    /// Used when the fingerprint was not extractable before UID scanning.
    public NoVerifiableUidException(String message) {
        super(message, Optional.empty());
    }
}
