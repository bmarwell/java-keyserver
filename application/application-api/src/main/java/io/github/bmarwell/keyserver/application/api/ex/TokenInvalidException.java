/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import java.util.Optional;

/// Thrown when a verification token is not found or does not match any pending queue entry.
public final class TokenInvalidException extends VerificationException {

    public TokenInvalidException(String message) {
        super(message, Optional.empty());
    }
}
