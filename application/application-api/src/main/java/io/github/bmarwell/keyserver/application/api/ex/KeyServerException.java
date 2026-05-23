/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;
import java.util.UUID;

/// Base class for all domain exceptions thrown by the keyserver.
///
/// Every instance carries a `correlationId` generated at construction time so
/// that the matching audit-log row and the `X-Correlation-ID` response header
/// all reference the same opaque identifier — without leaking any internal
/// detail to the caller.
///
/// `fingerprint` is absent when the error occurred before the key material
/// could be parsed (e.g. malformed armored input).
public abstract sealed class KeyServerException extends RuntimeException
        permits KeyParsingException,
                KeyValidationException,
                KeyNotFoundException,
                DuplicateKeyException,
                VerificationException {

    private final Optional<KeyFingerprint> fingerprint;
    private final String correlationId;

    protected KeyServerException(String message, Optional<KeyFingerprint> fingerprint) {
        super(message);
        this.fingerprint = fingerprint;
        this.correlationId = UUID.randomUUID().toString();
    }

    protected KeyServerException(String message, Throwable cause, Optional<KeyFingerprint> fingerprint) {
        super(message, cause);
        this.fingerprint = fingerprint;
        this.correlationId = UUID.randomUUID().toString();
    }

    public Optional<KeyFingerprint> getFingerprint() {
        return fingerprint;
    }

    /// Opaque correlation identifier for log correlation and the `X-Correlation-ID` response header.
    public String getCorrelationId() {
        return correlationId;
    }
}
