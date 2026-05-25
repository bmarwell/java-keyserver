/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.ex.KeyRevokedException;
import io.github.bmarwell.keyserver.application.api.ex.NoVerifiableUidException;
import io.github.bmarwell.keyserver.application.api.ex.TooManySubkeysException;
import io.github.bmarwell.keyserver.application.api.ex.TooManyVerifiableUidsException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;

/// Verifies parsed OpenPGP key rings against the server's add-path rules.
public class KeyRingVerifier
        implements VerificationStep<PGPPublicKeyRingCollection, KeySubmissionVerifier.VerifiedKeySubmission> {

    private final int maxEmailUids;
    private final int maxSubkeys;

    public KeyRingVerifier(int maxEmailUids, int maxSubkeys) {
        this.maxEmailUids = maxEmailUids;
        this.maxSubkeys = maxSubkeys;
    }

    @Override
    public KeySubmissionVerifier.VerifiedKeySubmission verify(PGPPublicKeyRingCollection keyRingCollection) {
        List<KeySubmissionVerifier.VerifiedKeyRing> verifiedKeyRings = new ArrayList<>();
        for (Iterator<PGPPublicKeyRing> rings = keyRingCollection.getKeyRings(); rings.hasNext(); ) {
            verifiedKeyRings.add(this.verifyKeyRing(rings.next()));
        }
        return new KeySubmissionVerifier.VerifiedKeySubmission(List.copyOf(verifiedKeyRings));
    }

    KeySubmissionVerifier.VerifiedKeyRing verifyKeyRing(PGPPublicKeyRing keyRing) {
        PGPPublicKey masterKey = keyRing.getPublicKey();
        String fingerprint = this.fingerprintHex(masterKey);

        if (masterKey.hasRevocation()) {
            throw new KeyRevokedException("Master key %s is revoked".formatted(fingerprint), () -> fingerprint);
        }

        int subkeyCount = this.countSubkeys(keyRing);
        if (subkeyCount > this.maxSubkeys) {
            throw new TooManySubkeysException(
                    "Key %s has %d subkeys, exceeding the limit of %d — submission rejected as potential DoS"
                            .formatted(fingerprint, subkeyCount, this.maxSubkeys),
                    () -> fingerprint);
        }

        List<String> emailUids = this.collectEmailUids(masterKey);
        if (emailUids.isEmpty()) {
            throw new NoVerifiableUidException(
                    "Key %s has no UIDs with a verifiable email address".formatted(fingerprint));
        }
        if (emailUids.size() > this.maxEmailUids) {
            throw new TooManyVerifiableUidsException(
                    "Key %s has %d email UIDs, exceeding the limit of %d — submission rejected as potential DoS"
                            .formatted(fingerprint, emailUids.size(), this.maxEmailUids));
        }

        return new KeySubmissionVerifier.VerifiedKeyRing(fingerprint, List.copyOf(emailUids));
    }

    List<String> collectEmailUids(PGPPublicKey masterKey) {
        List<String> emailUids = new ArrayList<>();
        for (Iterator<String> userIds = masterKey.getUserIDs(); userIds.hasNext(); ) {
            String uid = userIds.next();
            if (KeySubmissionVerifier.extractEmail(uid) != null) {
                emailUids.add(uid);
            }
        }
        return emailUids;
    }

    int countSubkeys(PGPPublicKeyRing keyRing) {
        int subkeyCount = 0;
        for (Iterator<PGPPublicKey> keys = keyRing.getPublicKeys(); keys.hasNext(); ) {
            PGPPublicKey key = keys.next();
            if (!key.isMasterKey()) {
                subkeyCount++;
            }
        }
        return subkeyCount;
    }

    private String fingerprintHex(PGPPublicKey key) {
        return HexFormat.of().formatHex(key.getFingerprint()).toUpperCase();
    }
}
