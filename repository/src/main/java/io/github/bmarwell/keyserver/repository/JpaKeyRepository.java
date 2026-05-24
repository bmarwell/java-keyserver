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
package io.github.bmarwell.keyserver.repository;

import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.repository.entity.KeyEntity;
import io.github.bmarwell.keyserver.repository.entity.UidEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

/// JPA adapter for the {@link KeyRepository} secondary port.
///
/// A key becomes visible in the public key store once at least one of its UIDs has
/// been email-verified.  Subsequent verifications for the same key append new UID
/// rows and update the stored armored block (which always reflects only the verified
/// UIDs as required by the privacy contract in the port Javadoc).
@Default
@ApplicationScoped
public class JpaKeyRepository extends BaseRepository implements KeyRepository {

    @Override
    @Transactional
    public void publishVerifiedUid(String fingerprint, String uidRaw, String uidEmail, String armoredKey) {
        KeyEntity key = getEntityManager().find(KeyEntity.class, fingerprint);

        if (key == null) {
            key = createKeyEntity(fingerprint, armoredKey);
            getEntityManager().persist(key);
        } else {
            // Update the armored block so it reflects newly verified UIDs (privacy: caller
            // is responsible for stripping unverified UIDs before passing armoredKey here).
            updateKey(key, armoredKey);
        }

        // Add the UID if it is not already present (idempotency guard for retries).
        if (!hasUid(key, uidRaw)) {
            UidEntity uid = new UidEntity(key, uidRaw);
            uid.setUidEmail(uidEmail);
            uid.setVerified(true);
            key.addUid(uid);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private KeyEntity createKeyEntity(String fingerprint, String armoredKey) {
        PGPPublicKey masterKey = parseMasterKey(armoredKey);
        OffsetDateTime creationTime =
                Instant.ofEpochMilli(masterKey.getCreationTime().getTime()).atOffset(ZoneOffset.UTC);
        String md5 = md5Hex(armoredKey);
        KeyEntity key = new KeyEntity(
                fingerprint, masterKey.getVersion(), masterKey.getAlgorithm(), creationTime, armoredKey, md5);
        if (masterKey.getValidSeconds() > 0) {
            key.setExpirationTime(creationTime.plusSeconds(masterKey.getValidSeconds()));
        }
        return key;
    }

    private void updateKey(KeyEntity key, String armoredKey) {
        // Use reflection-friendly setter approach: the entity exposes setArmoredKey and
        // setMtime via package-private mutation helpers below.
        key.setArmoredKey(armoredKey);
        key.setMtime(OffsetDateTime.now());
    }

    private boolean hasUid(KeyEntity key, String uidRaw) {
        // Check the in-memory collection first (avoids extra query when key was just
        // persisted and the collection is already populated).
        List<UidEntity> existing = key.getUids();
        for (UidEntity u : existing) {
            if (uidRaw.equals(u.getUidRaw())) {
                return true;
            }
        }
        // Fall back to a database query in case the collection is not yet initialised
        // (lazy-loaded proxy for a pre-existing key).
        Long count = getEntityManager()
                .createQuery(
                        "SELECT COUNT(u) FROM UidEntity u WHERE u.key.fingerprint = :fp AND u.uidRaw = :raw",
                        Long.class)
                .setParameter("fp", key.getFingerprint())
                .setParameter("raw", uidRaw)
                .getSingleResult();
        return count > 0;
    }

    private PGPPublicKey parseMasterKey(String armoredKey) {
        try {
            byte[] bytes = armoredKey.getBytes(StandardCharsets.UTF_8);
            try (var stream = PGPUtil.getDecoderStream(new ByteArrayInputStream(bytes))) {
                PGPPublicKeyRingCollection rings =
                        new PGPPublicKeyRingCollection(stream, new BcKeyFingerprintCalculator());
                PGPPublicKeyRing ring = rings.iterator().next();
                return ring.getPublicKey();
            }
        } catch (IOException | PGPException e) {
            throw new IllegalArgumentException("Failed to parse armored key: " + e.getMessage(), e);
        }
    }

    private String md5Hex(String armoredKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(armoredKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available in Java.
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }
}
