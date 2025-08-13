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
package io.github.bmarwell.keyserver.pgp.util;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import java.time.Instant;
import java.util.Iterator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.util.encoders.Hex;

public final class PgpKeyServerUtil {

    private PgpKeyServerUtil() {
        // utility class
    }

    public static PgpPublicKey getOnlyKeyFromKeyring(PGPPublicKeyRingCollection pgpPub) {
        PgpPublicKey foundKey = null;

        for (Iterator<PGPPublicKeyRing> rings = pgpPub.getKeyRings(); rings.hasNext(); ) {
            PGPPublicKeyRing keyring = rings.next();
            if (foundKey != null) {
                throw new IllegalArgumentException(
                        "Key already found, keyringCollection contains more than one keyring!");
            }

            foundKey = getPublicKeyFromKeyring(keyring, foundKey);
        }

        if (foundKey == null) {
            throw new IllegalArgumentException("Keyring does not contain any public keys");
        }

        return foundKey;
    }

    private static PgpPublicKey getPublicKeyFromKeyring(PGPPublicKeyRing keyring, PgpPublicKey foundKey) {
        for (Iterator<PGPPublicKey> keys = keyring.getPublicKeys(); keys.hasNext(); ) {
            foundKey = handleFoundKey(foundKey, keys);
        }

        return foundKey;
    }

    private static PgpPublicKey handleFoundKey(PgpPublicKey foundKey, Iterator<PGPPublicKey> keys) {
        final var key = keys.next();

        if (key.isMasterKey() && foundKey != null) {
            throw new IllegalArgumentException("Keyring contains multiple public keys");
        }

        if (key.isMasterKey()) {
            return handleFoundMasterKey(key, foundKey);
        }

        // key is subkey
        key.hasRevocation();
        return foundKey;
    }

    private static PgpPublicKey handleFoundMasterKey(PGPPublicKey key, PgpPublicKey foundKey) {
        final var fp = new KeyFingerprint(Hex.toHexString(key.getFingerprint()));
        final var ctime = key.getCreationTime().toInstant();
        final var validSeconds = key.getValidSeconds();
        final Instant expiry;

        if (validSeconds == 0) {
            expiry = Instant.MAX;
        } else {
            expiry = Instant.ofEpochSecond(ctime.getEpochSecond()).plusSeconds(validSeconds);
        }

        foundKey = new PgpPublicKey(fp, ctime, expiry);

        System.out.println("next key");
        System.out.println("is master key: " + key.isMasterKey());
        System.out.println("can encrypt: " + key.isEncryptionKey());
        System.out.println("algo: " + key.getAlgorithm());
        System.out.println("bits: " + key.getBitStrength());

        for (Iterator<String> userIds = key.getUserIDs(); userIds.hasNext(); ) {
            String userId = userIds.next();
            System.out.println(userId);
        }

        System.out.println(Long.toHexString(key.getKeyID()));
        System.out.println();

        return foundKey;
    }
}
