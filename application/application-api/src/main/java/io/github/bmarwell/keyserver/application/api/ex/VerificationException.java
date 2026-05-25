/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Base for exceptions raised during the email-verification flow.
public abstract sealed class VerificationException extends KeyServerException
        permits TokenExpiredException, TokenInvalidException {

    protected VerificationException(String message, Optional<KeyFingerprint> fingerprint) {
        super(message, fingerprint);
    }
}
