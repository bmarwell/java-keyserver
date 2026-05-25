/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import java.nio.charset.StandardCharsets;

/// Validates the raw armored-key payload size before any OpenPGP parsing happens.
public class KeyTextSizeValidator implements VerificationStep<KeyTextSizeValidator.Input, byte[]> {

    @Override
    public byte[] verify(Input input) {
        String keyText = input.keyText();
        if (keyText == null || keyText.isBlank()) {
            throw new KeyParsingException("keytext must not be null or blank");
        }

        int effectiveMaxKeyBytes =
                input.configuredMaxKeyBytes() > 0 ? input.configuredMaxKeyBytes() : input.defaultMaxKeyBytes();
        if (keyText.length() > effectiveMaxKeyBytes) {
            throw new KeyParsingException("Key submission exceeds maximum allowed size");
        }

        byte[] keyTextBytes = keyText.getBytes(StandardCharsets.UTF_8);
        if (keyTextBytes.length > effectiveMaxKeyBytes) {
            throw new KeyParsingException("Key submission exceeds maximum allowed size");
        }

        return keyTextBytes;
    }

    public record Input(String keyText, int configuredMaxKeyBytes, int defaultMaxKeyBytes) {}
}
