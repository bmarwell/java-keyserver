/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.ex;

import java.util.Optional;

/// Thrown when the submitted key material cannot be decoded at all —
/// e.g. the ASCII armor is corrupted or the byte stream is not a valid OpenPGP structure.
/// No fingerprint is available because parsing failed before one could be extracted.
public final class KeyParsingException extends KeyServerException {

    public KeyParsingException(String message, Throwable cause) {
        super(message, cause, Optional.empty());
    }

    public KeyParsingException(String message) {
        super(message, Optional.empty());
    }
}
