/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.ex.TooManySubkeysException;
import io.github.bmarwell.keyserver.application.api.ex.TooManyVerifiableUidsException;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.junit.jupiter.api.Test;

class KeyRingVerifierTest {

    private static final int MAX_EMAIL_UIDS = 20;
    private static final int MAX_SUBKEYS = 50;

    @Test
    void rejects_key_ring_with_too_many_email_uids() throws Exception {
        int overLimit = MAX_EMAIL_UIDS + 1;
        KeyRingVerifier verifier = new KeyRingVerifier(MAX_EMAIL_UIDS, MAX_SUBKEYS) {
            @Override
            List<String> collectEmailUids(PGPPublicKey masterKey) {
                return IntStream.range(0, overLimit)
                        .mapToObj(i -> "user%d@example.com".formatted(i))
                        .toList();
            }
        };

        assertThatThrownBy(() -> verifier.verify(loadTestKeyRingCollection()))
                .isInstanceOf(TooManyVerifiableUidsException.class)
                .hasMessageContaining(String.valueOf(overLimit))
                .hasMessageContaining(String.valueOf(MAX_EMAIL_UIDS));
    }

    @Test
    void rejects_key_ring_with_too_many_subkeys() throws Exception {
        int overLimit = MAX_SUBKEYS + 1;
        KeyRingVerifier verifier = new KeyRingVerifier(MAX_EMAIL_UIDS, MAX_SUBKEYS) {
            @Override
            int countSubkeys(PGPPublicKeyRing keyRing) {
                return overLimit;
            }
        };

        assertThatThrownBy(() -> verifier.verify(loadTestKeyRingCollection()))
                .isInstanceOf(TooManySubkeysException.class)
                .hasMessageContaining(String.valueOf(overLimit))
                .hasMessageContaining(String.valueOf(MAX_SUBKEYS));
    }

    private PGPPublicKeyRingCollection loadTestKeyRingCollection() throws Exception {
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream("/pgp/test-key-with-email.asc"))) {
            byte[] keyTextBytes = stream.readAllBytes();
            try (var decoderStream = PGPUtil.getDecoderStream(new java.io.ByteArrayInputStream(keyTextBytes))) {
                return new PGPPublicKeyRingCollection(decoderStream, new BcKeyFingerprintCalculator());
            }
        }
    }
}
