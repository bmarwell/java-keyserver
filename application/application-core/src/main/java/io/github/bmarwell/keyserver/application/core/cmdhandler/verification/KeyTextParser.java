/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

/// Parses validated armored key bytes into a Bouncy Castle key-ring collection.
public class KeyTextParser implements VerificationStep<byte[], PGPPublicKeyRingCollection> {

    @Override
    public PGPPublicKeyRingCollection verify(byte[] keyBytes) {
        try {
            try (var decoderStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(keyBytes))) {
                return new PGPPublicKeyRingCollection(decoderStream, new BcKeyFingerprintCalculator());
            }
        } catch (IOException | PGPException e) {
            throw new KeyParsingException("Failed to parse PGP key: " + e.getMessage(), e);
        }
    }
}
