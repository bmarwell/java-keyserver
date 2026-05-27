/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository;

import io.github.bmarwell.keyserver.application.api.KeyIndexResult;
import io.github.bmarwell.keyserver.application.api.UidIndexEntry;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository.KeySearchResult;
import io.github.bmarwell.keyserver.repository.entity.KeyEntity;
import io.github.bmarwell.keyserver.repository.entity.UidEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
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

    /// Maximum number of keys returned by multi-result index queries.
    ///
    /// A broad search term (e.g. a common email domain or UID substring) could otherwise
    /// load an unbounded number of key rows — together with their UID collections — into a
    /// single JPA transaction.  5 000 matches what popular HKP clients are expected to handle
    /// and prevents memory exhaustion under adversarial inputs.
    private static final int INDEX_RESULT_LIMIT = 5_000;

    // -------------------------------------------------------------------------
    // JPA provider read-only query hints.
    //
    // Index queries map results directly to immutable DTOs; the entities are
    // never written back.  Telling each provider to skip dirty tracking and lock
    // acquisition reduces heap pressure and eliminates unnecessary lock rows.
    // Unknown hints are silently ignored by every compliant JPA provider.
    // -------------------------------------------------------------------------

    /// Hibernate: mark loaded entities as read-only so dirty checking is skipped.
    private static final String HINT_HIBERNATE_READ_ONLY = "org.hibernate.readOnly";

    /// EclipseLink: detach objects immediately after loading (read-only session).
    private static final String HINT_ECLIPSELINK_READ_ONLY = "eclipselink.read-only";

    /// Apache OpenJPA: acquire no read lock, reducing contention on index queries.
    private static final String HINT_OPENJPA_READ_LOCK_MODE = "openjpa.FetchPlan.ReadLockMode";

    @Override
    @Transactional
    public void publishVerifiedUid(String fingerprint, String uidRaw, String uidEmail, String armoredKey) {
        // Fast-path: if the key row already exists we can skip the expensive Bouncy Castle parse
        // and the native INSERT entirely.  The PESSIMISTIC_WRITE find below will still serialise
        // concurrent UID publications for the same fingerprint.
        //
        // Concurrent first-publish race: two transactions may both see null here and both
        // attempt the INSERT.  ON CONFLICT (fingerprint) DO NOTHING handles that safely —
        // the second transaction blocks until the first commits, then silently skips the insert.
        // The PESSIMISTIC_WRITE find below then serialises both for the armored-key rewrite.
        if (getEntityManager().find(KeyEntity.class, fingerprint) == null) {
            // Parse the uploaded key block for metadata — only needed for the initial row.
            PGPPublicKey masterKey = parseMasterKey(armoredKey);
            OffsetDateTime creationTime =
                    Instant.ofEpochMilli(masterKey.getCreationTime().getTime()).atOffset(ZoneOffset.UTC);
            long validSeconds = masterKey.getValidSeconds();
            Timestamp expTime = validSeconds > 0
                    ? Timestamp.from(creationTime.plusSeconds(validSeconds).toInstant())
                    : null;
            Integer bitStrength = masterKey.getBitStrength() > 0 ? masterKey.getBitStrength() : null;

            // Strip to just the new UID for the initial row — overwritten unconditionally below,
            // but needed to satisfy the armored_key NOT NULL constraint on a fresh insert.
            String initialStripped = stripToVerifiedUids(armoredKey, Set.of(uidRaw));
            String initialMd5 = md5Hex(initialStripped);

            getEntityManager()
                    .createNativeQuery("INSERT INTO keys"
                            + " (fingerprint, version, algorithm, bit_strength,"
                            + "  creation_time, expiration_time, revoked, armored_key, md5)"
                            + " VALUES (:fp, :ver, :alg, :bs, :ct, :et, false, :ak, :md5)"
                            + " ON CONFLICT (fingerprint) DO NOTHING")
                    .setParameter("fp", fingerprint)
                    .setParameter("ver", masterKey.getVersion())
                    .setParameter("alg", masterKey.getAlgorithm())
                    .setParameter("bs", bitStrength)
                    .setParameter("ct", Timestamp.from(creationTime.toInstant()))
                    .setParameter("et", expTime)
                    .setParameter("ak", initialStripped)
                    .setParameter("md5", initialMd5)
                    .executeUpdate();
        }

        // Acquire an exclusive row lock — serialises concurrent UID publications for
        // the same key from this point forward.  The row is guaranteed to exist after
        // the INSERT above, so PESSIMISTIC_WRITE will always find a row to lock.
        KeyEntity key = getEntityManager().find(KeyEntity.class, fingerprint, LockModeType.PESSIMISTIC_WRITE);

        // Reload the set of already-committed verified UIDs (from prior transactions),
        // add the new UID, and re-strip the original upload block.  This guarantees
        // the stored armored block always contains exactly the verified-UID set —
        // no more, no less — regardless of which transaction ran first.
        Set<String> verifiedUids = loadVerifiedUidRaws(fingerprint);
        verifiedUids.add(uidRaw);
        String stripped = stripToVerifiedUids(armoredKey, verifiedUids);
        updateKey(key, stripped);

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

        // Key-ID/fingerprint: starts with 0x or is all-hex with a valid key-ID/fingerprint length.
        // Only take this path for lengths 8 (short key ID), 16 (long key ID), 40 (v4 fingerprint),
        // or 64 (v5 fingerprint).  All-hex strings of other lengths (e.g. "cafe", a colour name
        // that happens to be hex) must fall through to UID-substring search.
        String hexCandidate = normalized.startsWith("0x") ? normalized.substring(2) : normalized;
        if ((normalized.startsWith("0x") || isHexString(hexCandidate)) && isValidKeyIdLength(hexCandidate.length())) {
            return findByKeyIdOrFingerprint(hexCandidate);
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

    @Override
    @Transactional
    public List<KeyIndexResult> findManyBySearch(String search, boolean exactMatch) {
        if (search == null || search.isBlank()) {
            return List.of();
        }

        String normalized = search.strip();

        String hexCandidate = normalized.startsWith("0x") ? normalized.substring(2) : normalized;
        if ((normalized.startsWith("0x") || isHexString(hexCandidate)) && isValidKeyIdLength(hexCandidate.length())) {
            return findManyByKeyIdOrFingerprint(hexCandidate);
        }

        if (normalized.contains("@")) {
            return findManyByEmail(normalized.toLowerCase(Locale.ROOT), exactMatch);
        }

        if (exactMatch) {
            return List.of();
        }
        return findManyByUidSubstring(normalized);
    }

    private Optional<KeySearchResult> findByKeyIdOrFingerprint(String hexValue) {
        // Normalise to uppercase: fingerprint (and the DB-generated keyid_long column) are stored
        // as uppercase.  Avoiding LOWER() on the column allows the DB to use the keys_keyid_long
        // index and the keys_rfingerprint text_pattern_ops index.
        String upper = hexValue.toUpperCase(Locale.ROOT);
        int len = upper.length();

        if (len == 40 || len == 64) {
            return toResult(getEntityManager().find(KeyEntity.class, upper));
        }

        if (len == 16) {
            List<KeyEntity> results = getEntityManager()
                    .createQuery("SELECT k FROM KeyEntity k WHERE k.keyidLong = :keyId", KeyEntity.class)
                    .setParameter("keyId", upper)
                    .setMaxResults(1)
                    .getResultList();
            return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
        }

        // Short key ID (8 chars): reverse it and use the rfingerprint text_pattern_ops index for a
        // prefix scan.
        String reversedShortId = new StringBuilder(upper).reverse().toString();
        List<KeyEntity> results = getEntityManager()
                .createQuery("SELECT k FROM KeyEntity k WHERE k.rfingerprint LIKE :prefix", KeyEntity.class)
                .setParameter("prefix", reversedShortId + "%")
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private List<KeyIndexResult> findManyByKeyIdOrFingerprint(String hexValue) {
        String upper = hexValue.toUpperCase(Locale.ROOT);
        int len = upper.length();

        if (len == 40 || len == 64) {
            // Full fingerprint — switch from em.find() to JPQL so JOIN FETCH eagerly loads UIDs.
            List<KeyEntity> results = applyReadOnlyHints(getEntityManager()
                            .createQuery(
                                    "SELECT DISTINCT k FROM KeyEntity k JOIN FETCH k.uids"
                                            + " WHERE k.fingerprint = :fp",
                                    KeyEntity.class)
                            .setParameter("fp", upper))
                    .getResultList();
            return toIndexResults(results);
        }

        if (len == 16) {
            List<KeyEntity> results = applyReadOnlyHints(getEntityManager()
                            .createQuery(
                                    "SELECT DISTINCT k FROM KeyEntity k JOIN FETCH k.uids"
                                            + " WHERE k.keyidLong = :keyId",
                                    KeyEntity.class)
                            .setParameter("keyId", upper))
                    .getResultList();
            return toIndexResults(results);
        }

        // Short key ID (8 chars): reverse it and use the rfingerprint text_pattern_ops index for a
        // prefix scan.  reverse(fingerprint) starts with reverse(shortKeyId), so this matches the
        // last 8 chars of the fingerprint without a leading-wildcard LIKE on the forward column.
        String reversedShortId = new StringBuilder(upper).reverse().toString();
        List<KeyEntity> results = applyReadOnlyHints(getEntityManager()
                        .createQuery(
                                "SELECT DISTINCT k FROM KeyEntity k JOIN FETCH k.uids"
                                        + " WHERE k.rfingerprint LIKE :prefix",
                                KeyEntity.class)
                        .setParameter("prefix", reversedShortId + "%"))
                .getResultList();
        return toIndexResults(results);
    }

    private Optional<KeySearchResult> findByEmail(String email, boolean exactMatch) {
        List<KeyEntity> results = queryEntitiesByEmail(email, exactMatch, 1);
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private List<KeyIndexResult> findManyByEmail(String email, boolean exactMatch) {
        List<String> fingerprints = queryFingerprintsByEmail(email, exactMatch);
        return fingerprints.isEmpty() ? List.of() : fetchIndexResultsByFingerprints(fingerprints);
    }

    /// Returns at most one KeyEntity row for a given email filter (single-result path).
    ///
    /// Keeps the JPQL in one place; callers must always pass {@code maxResults = 1}.
    /// The multi-result path uses {@link #queryFingerprintsByEmail} instead.
    private List<KeyEntity> queryEntitiesByEmail(String email, boolean exactMatch, int maxResults) {
        String jpql = exactMatch
                ? "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) = :email AND u.verified = true"
                : "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) LIKE :email AND u.verified = true";
        String param = exactMatch ? email : "%" + email + "%";
        return getEntityManager()
                .createQuery(jpql, KeyEntity.class)
                .setParameter("email", param)
                .setMaxResults(maxResults)
                .getResultList();
    }

    /// First query of the two-query pattern for email index searches.
    ///
    /// Selects fingerprints only (no collection fetch) so that {@link #INDEX_RESULT_LIMIT}
    /// is applied correctly at the SQL level.  The caller then passes the fingerprint list
    /// to {@link #fetchIndexResultsByFingerprints} which loads the full entity graph
    /// via {@code JOIN FETCH} without any conflicting pagination.
    private List<String> queryFingerprintsByEmail(String email, boolean exactMatch) {
        String jpql = exactMatch
                ? "SELECT DISTINCT k.fingerprint FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) = :email AND u.verified = true"
                : "SELECT DISTINCT k.fingerprint FROM KeyEntity k JOIN k.uids u"
                        + " WHERE LOWER(u.uidEmail) LIKE :email AND u.verified = true";
        String param = exactMatch ? email : "%" + email + "%";
        return applyReadOnlyHints(getEntityManager()
                        .createQuery(jpql, String.class)
                        .setParameter("email", param)
                        .setMaxResults(INDEX_RESULT_LIMIT))
                .getResultList();
    }

    private Optional<KeySearchResult> findByUidSubstring(String term) {
        List<KeyEntity> results = queryEntitiesByUidSubstring(term, 1);
        return results.isEmpty() ? Optional.empty() : toResult(results.get(0));
    }

    private List<KeyIndexResult> findManyByUidSubstring(String term) {
        List<String> fingerprints = queryFingerprintsByUidSubstring(term);
        return fingerprints.isEmpty() ? List.of() : fetchIndexResultsByFingerprints(fingerprints);
    }

    /// Returns at most one KeyEntity row whose raw UID text contains the given term (single-result path).
    ///
    /// Callers must always pass {@code maxResults = 1}.
    /// The multi-result path uses {@link #queryFingerprintsByUidSubstring} instead.
    private List<KeyEntity> queryEntitiesByUidSubstring(String term, int maxResults) {
        return getEntityManager()
                .createQuery(
                        "SELECT DISTINCT k FROM KeyEntity k JOIN k.uids u"
                                + " WHERE LOWER(u.uidRaw) LIKE :term AND u.verified = true",
                        KeyEntity.class)
                .setParameter("term", "%" + term.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(maxResults)
                .getResultList();
    }

    /// First query of the two-query pattern for UID substring index searches.
    ///
    /// Selects fingerprints only so {@link #INDEX_RESULT_LIMIT} is applied at the SQL level.
    private List<String> queryFingerprintsByUidSubstring(String term) {
        return applyReadOnlyHints(getEntityManager()
                        .createQuery(
                                "SELECT DISTINCT k.fingerprint FROM KeyEntity k JOIN k.uids u"
                                        + " WHERE LOWER(u.uidRaw) LIKE :term AND u.verified = true",
                                String.class)
                        .setParameter("term", "%" + term.toLowerCase(Locale.ROOT) + "%")
                        .setMaxResults(INDEX_RESULT_LIMIT))
                .getResultList();
    }

    /// Second query of the two-query pattern: loads full key entities with their UIDs eagerly.
    ///
    /// Because the fingerprint list was already bounded by {@link #INDEX_RESULT_LIMIT} in the
    /// first query, this {@code JOIN FETCH} query has no conflicting pagination and the JPA
    /// provider can apply the join at the SQL level without in-memory row reduction.
    private List<KeyIndexResult> fetchIndexResultsByFingerprints(List<String> fingerprints) {
        List<KeyEntity> entities = applyReadOnlyHints(getEntityManager()
                        .createQuery(
                                "SELECT DISTINCT k FROM KeyEntity k JOIN FETCH k.uids" + " WHERE k.fingerprint IN :fps",
                                KeyEntity.class)
                        .setParameter("fps", fingerprints))
                .getResultList();
        return toIndexResults(entities);
    }

    private static Optional<KeySearchResult> toResult(KeyEntity key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.of(new KeySearchResult(key.getFingerprint(), key.getArmoredKey()));
    }

    private static List<KeyIndexResult> toIndexResults(List<KeyEntity> keys) {
        return keys.stream().map(JpaKeyRepository::toIndexResult).toList();
    }

    private static KeyIndexResult toIndexResult(KeyEntity key) {
        List<UidIndexEntry> uids = key.getUids().stream()
                .filter(UidEntity::isVerified)
                .map(u -> new UidIndexEntry(u.getUidRaw(), u.getCreationTime(), u.getExpirationTime(), u.isRevoked()))
                .toList();

        return new KeyIndexResult(
                key.getFingerprint(),
                key.getAlgorithm(),
                key.getBitStrength(),
                key.getCreationTime(),
                key.getExpirationTime(),
                key.isRevoked(),
                key.isDisabled(),
                uids);
    }

    /// Applies read-only query hints for the three major JPA providers.
    ///
    /// Index queries map results directly to immutable {@link KeyIndexResult} DTOs —
    /// the loaded entities are never modified.  Signalling read-only intent allows each
    /// provider to skip dirty tracking and lock acquisition:
    /// <ul>
    ///   <li>Hibernate ({@code org.hibernate.readOnly = true}): skips snapshot creation and
    ///       dirty checking at flush time.</li>
    ///   <li>EclipseLink ({@code eclipselink.read-only = true}): detaches objects immediately,
    ///       preventing them from entering the identity map.</li>
    ///   <li>Apache OpenJPA ({@code openjpa.FetchPlan.ReadLockMode = "NONE"}): suppresses
    ///       implicit read-lock acquisition on each loaded row.</li>
    /// </ul>
    /// Unknown hints are silently ignored by all compliant JPA providers, so this method
    /// is safe to call regardless of which provider is active at runtime.
    private static <T> TypedQuery<T> applyReadOnlyHints(TypedQuery<T> query) {
        return query.setHint(HINT_HIBERNATE_READ_ONLY, Boolean.TRUE)
                .setHint(HINT_ECLIPSELINK_READ_ONLY, Boolean.TRUE)
                .setHint(HINT_OPENJPA_READ_LOCK_MODE, "NONE");
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

    private static boolean isValidKeyIdLength(int len) {
        // 8 = short key ID, 16 = long key ID, 40 = v4 fingerprint, 64 = v5 fingerprint.
        return len == 8 || len == 16 || len == 40 || len == 64;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
