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
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository.KeySearchResult;
import io.github.bmarwell.keyserver.repository.entity.KeyEntity;
import io.github.bmarwell.keyserver.repository.entity.UidEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
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
            // Privacy: strip all UIDs except the one being verified now.
            // The queued armoredKey is the raw user upload containing ALL UIDs.
            // We must not publish unverified identities — only serve what the owner
            // has actively confirmed via the email link.
            String stripped = stripToVerifiedUids(armoredKey, Set.of(uidRaw));
            key = createKeyEntity(fingerprint, stripped);
            getEntityManager().persist(key);
        } else {
            // Build the set of already-verified UIDs plus the new one being confirmed.
            // Re-strip from the original armored key (which was stored in the queue) so
            // the stored block always reflects exactly the verified-UID set and nothing more.
            Set<String> verifiedUids = loadVerifiedUidRaws(fingerprint);
            verifiedUids.add(uidRaw);
            String stripped = stripToVerifiedUids(armoredKey, verifiedUids);
            updateKey(key, stripped);
        }

        // Add the UID row if not already present (idempotency guard for retries).
        if (!hasUid(key.getFingerprint(), uidRaw)) {
            UidEntity uid = new UidEntity(key, uidRaw);
            uid.setUidEmail(uidEmail);
            uid.setVerified(true);
            key.addUid(uid);
        }
    }

    // -------------------------------------------------------------------------
    // Search queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Optional<KeySearchResult> findBySearch(String search, boolean exactMatch) {
        if (search == null || search.isBlank()) {
            return Optional.empty();
        }

        String normalized = search.strip();

        // Fingerprint or key-ID search: starts with 0x or is all-hex
        if (normalized.startsWith("0x") || isHexString(normalized)) {
            return findByKeyIdOrFingerprint(normalized.startsWith("0x") ? normalized.substring(2) : normalized);
        }

        // Email search
        if (normalized.contains("@")) {
            return findByEmail(normalized.toLowerCase(Locale.ROOT), exactMatch);
        }

        // Fallback: UID substring search (only for non-exact)
        if (exactMatch) {
            return Optional.empty();
        }
        return findByUidSubstring(normalized);
    }

    private Optional<KeySearchResult> findByKeyIdOrFingerprint(String hexValue) {
        String lower = hexValue.toLowerCase(Locale.ROOT);
        int len = lower.length();
        if (len == 40 || len == 64) {
            // Full fingerprint — try uppercase first (as stored by the handler)
            KeyEntity key = getEntityManager().find(KeyEntity.class, lower.toUpperCase(Locale.ROOT));
            if (key == null) {
                key = getEntityManager().find(KeyEntity.class, lower);
            }
            return toResult(key);
        }
        // Short (8) or long (16) key ID — match via keyid_long suffix
        String suffix = len == 8 ? "%" + lower : lower;
        List<KeyEntity> results = getEntityManager()
                .createQuery("SELECT k FROM KeyEntity k WHERE LOWER(k.keyidLong) LIKE :suffix", KeyEntity.class)
                .setParameter("suffix", suffix)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private Optional<KeySearchResult> findByEmail(String email, boolean exactMatch) {
        String jpql = exactMatch
                ? "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) = :email AND u.verified = true"
                : "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) LIKE :email AND u.verified = true";
        String param = exactMatch ? email : "%" + email + "%";
        List<KeyEntity> results = getEntityManager()
                .createQuery(jpql, KeyEntity.class)
                .setParameter("email", param)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private Optional<KeySearchResult> findByUidSubstring(String term) {
        List<KeyEntity> results = getEntityManager()
                .createQuery(
                        "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                                + " WHERE LOWER(u.uidRaw) LIKE :term AND u.verified = true",
                        KeyEntity.class)
                .setParameter("term", "%" + term.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private static Optional<KeySearchResult> toResult(KeyEntity key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.of(new KeySearchResult(key.getFingerprint(), key.getArmoredKey()));
    }

    private static boolean isHexString(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
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
        key.setArmoredKey(armoredKey);
        // Keep the MD5 checksum in sync with the stored armored block.
        // The checksum drifts if we update the block without recomputing it, breaking
        // any deduplication or integrity checks that rely on this column.
        key.setMd5(md5Hex(armoredKey));
        key.setMtime(OffsetDateTime.now());
    }

    /// Returns whether a UID row already exists for the given fingerprint and raw UID string.
    ///
    /// Uses a single `COUNT` query rather than loading the full UID collection into memory.
    /// Loading all UIDs to check one can be expensive for keys with many verified UIDs.
    private boolean hasUid(String fingerprint, String uidRaw) {
        Long count = getEntityManager()
                .createQuery(
                        "SELECT COUNT(u) FROM UidEntity u WHERE u.key.fingerprint = :fp AND u.uidRaw = :raw",
                        Long.class)
                .setParameter("fp", fingerprint)
                .setParameter("raw", uidRaw)
                .getSingleResult();
        return count > 0;
    }

    /// Returns the raw UID strings of all currently verified UIDs for a key.
    ///
    /// Used to compute the "UIDs to keep" set before stripping unverified identities
    /// from the armored block.  Returns a mutable set so the caller can add the new UID.
    private Set<String> loadVerifiedUidRaws(String fingerprint) {
        return getEntityManager()
                .createQuery(
                        "SELECT u.uidRaw FROM UidEntity u WHERE u.key.fingerprint = :fp AND u.verified = true",
                        String.class)
                .setParameter("fp", fingerprint)
                .getResultStream()
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }

    /// Parses the armored block and returns the master public key of the first ring.
    ///
    /// @throws IllegalArgumentException if the input contains no key rings or is malformed.
    private PGPPublicKey parseMasterKey(String armoredKey) {
        return parseKeyRing(armoredKey).getPublicKey();
    }

    /// Parses the armored block and returns the first {@link PGPPublicKeyRing}.
    ///
    /// @throws IllegalArgumentException if the input contains no key rings or is malformed.
    private PGPPublicKeyRing parseKeyRing(String armoredKey) {
        try {
            byte[] bytes = armoredKey.getBytes(StandardCharsets.UTF_8);
            try (var stream = PGPUtil.getDecoderStream(new ByteArrayInputStream(bytes))) {
                PGPPublicKeyRingCollection rings =
                        new PGPPublicKeyRingCollection(stream, new BcKeyFingerprintCalculator());
                Iterator<PGPPublicKeyRing> it = rings.iterator();
                if (!it.hasNext()) {
                    throw new IllegalArgumentException("Armored input decodes but contains no public key rings");
                }
                return it.next();
            }
        } catch (IOException | PGPException e) {
            throw new IllegalArgumentException("Failed to parse armored key: " + e.getMessage(), e);
        }
    }

    /// Strips all UIDs except those in `uidRawsToKeep` from the armored key block and
    /// re-exports the result as ASCII armor.
    ///
    /// ## Privacy contract
    ///
    /// The raw upload may contain many UIDs (name/email combinations).  We must never
    /// serve UIDs the owner has not verified via the email flow.  This method removes
    /// the OpenPGP certification packets for every unverified UID so the stored block
    /// — and therefore the HKP `op=get` response — only contains verified identities.
    ///
    /// ## Note on duplicated BC parsing
    ///
    /// `AddKeyToVerificationQueueCommandHandler` also parses armored key blocks using
    /// Bouncycastle.  The duplication is intentional: the handler lives in the
    /// application core (infrastructure-free) while this class is the infrastructure
    /// adapter; sharing code would violate the hexagonal architecture layer boundary.
    /// A shared `pgp-utils` module could be introduced if the duplication grows.
    private String stripToVerifiedUids(String armoredKey, Set<String> uidRawsToKeep) {
        try {
            PGPPublicKeyRing ring = parseKeyRing(armoredKey);
            PGPPublicKey masterKey = ring.getPublicKey();

            // Collect UIDs to remove first (avoids ConcurrentModificationException
            // when mutating certifications while iterating).
            List<String> uidsToRemove = new ArrayList<>();
            for (Iterator<String> it = masterKey.getUserIDs(); it.hasNext(); ) {
                String uid = it.next();
                if (!uidRawsToKeep.contains(uid)) {
                    uidsToRemove.add(uid);
                }
            }

            // Remove all certification signatures for each unwanted UID.
            // PGPPublicKey.removeCertification returns a new immutable instance each time.
            for (String uid : uidsToRemove) {
                List<PGPSignature> sigsToRemove = new ArrayList<>();
                for (Iterator<PGPSignature> sigs = masterKey.getSignaturesForID(uid); sigs.hasNext(); ) {
                    sigsToRemove.add(sigs.next());
                }
                for (PGPSignature sig : sigsToRemove) {
                    masterKey = PGPPublicKey.removeCertification(masterKey, uid, sig);
                }
            }

            // Rebuild the ring with the stripped master key and re-export to ASCII armor.
            ring = PGPPublicKeyRing.insertPublicKey(ring, masterKey);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArmoredOutputStream armor = new ArmoredOutputStream(out)) {
                ring.encode(armor);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to strip UIDs from armored key: " + e.getMessage(), e);
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
