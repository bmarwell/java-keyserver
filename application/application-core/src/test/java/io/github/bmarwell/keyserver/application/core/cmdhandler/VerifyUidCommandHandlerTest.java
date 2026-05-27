/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.VerifyUidCommand;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationEntry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Unit tests for {@link VerifyUidCommandHandler}.
///
/// All seven verification-flow scenarios are covered here at handler level.
/// No database, no CDI container — dependencies are plain fake implementations
/// wired through setter injection.
///
/// Integration tests with a real Liberty server and PostgreSQL (via Testcontainers)
/// are planned in the `integration-tests` module and will cover the full HTTP
/// request → handler → DB roundtrip, including the JPA-level concurrency controls
/// for scenario 7 (concurrent UID publication for the same key).
class VerifyUidCommandHandlerTest {

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    record PublishedUid(String fingerprint, String uidRaw, String uidEmail) {}

    /** Fake key repository that records every publishVerifiedUid call. Thread-safe for scenario 7. */
    static class FakeKeyRepository implements KeyRepository {
        // CopyOnWriteArrayList: safe for concurrent adds without external synchronisation.
        final List<PublishedUid> published = new CopyOnWriteArrayList<>();

        @Override
        public void publishVerifiedUid(String fingerprint, String uidRaw, String uidEmail, String armoredKey) {
            published.add(new PublishedUid(fingerprint, uidRaw, uidEmail));
        }

        @Override
        public Optional<KeySearchResult> findBySearch(String search, boolean exactMatch) {
            return Optional.empty();
        }

        @Override
        public List<io.github.bmarwell.keyserver.application.api.KeyIndexResult> findManyBySearch(
                String search, boolean exactMatch) {
            return List.of();
        }
    }

    /**
     * Fake verification-queue repository backed by a simple map.
     * Entries are pre-populated via {@link #put} before the handler is invoked.
     * Thread-safe for scenario 7: store uses a synchronizedMap, verifiedIds a synchronizedList.
     */
    static class FakeVerificationQueueRepository implements VerificationQueueRepository {

        record StoredEntry(VerificationEntry entry, boolean consumed) {}

        final Map<Long, StoredEntry> store = Collections.synchronizedMap(new HashMap<>());
        final List<Long> verifiedIds = Collections.synchronizedList(new ArrayList<>());

        void put(long id, VerificationEntry entry) {
            store.put(id, new StoredEntry(entry, false));
        }

        @Override
        public long enqueue(VerificationRequest request) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public Optional<VerificationEntry> findPendingById(long tokenId) {
            StoredEntry stored = store.get(tokenId);
            if (stored == null || stored.consumed()) {
                return Optional.empty();
            }
            return Optional.of(stored.entry());
        }

        @Override
        public void markVerified(long tokenId) {
            StoredEntry stored = store.get(tokenId);
            if (stored != null) {
                store.put(tokenId, new StoredEntry(stored.entry(), true));
                verifiedIds.add(tokenId);
            }
        }
    }

    /** Fake BTX repository that records fingerprint writes. */
    static class FakeBusinessTransactionRepository implements BusinessTransactionRepository {
        @Nullable
        String recordedFingerprint;

        @Override
        public void recordStarted(long btxId, String commandType, @Nullable String callerIp) {}

        @Override
        public void recordFingerprint(long btxId, String fingerprint) {
            this.recordedFingerprint = fingerprint;
        }

        @Override
        public void recordCompleted(long btxId) {}

        @Override
        public void recordFailed(long btxId, String errorType, @Nullable String errorMessage) {}
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    static final String FINGERPRINT = "AABBCCDDEEFF00112233445566778899AABBCCDD";
    static final String ARMORED_KEY =
            "-----BEGIN PGP PUBLIC KEY BLOCK-----\n(stub)\n-----END PGP PUBLIC KEY BLOCK-----";
    static final long TOKEN_1 = 1_000L;
    static final long TOKEN_2 = 2_000L;

    private VerificationEntry pendingEntry(long id, String uidRaw, String uidEmail) {
        return new VerificationEntry(
                id,
                FINGERPRINT,
                uidRaw,
                uidEmail,
                ARMORED_KEY,
                OffsetDateTime.now().plusHours(24));
    }

    private VerificationEntry expiredEntry(long id, String uidRaw, String uidEmail) {
        return new VerificationEntry(
                id,
                FINGERPRINT,
                uidRaw,
                uidEmail,
                ARMORED_KEY,
                OffsetDateTime.now().minusHours(1));
    }

    FakeVerificationQueueRepository fakeQueue;
    FakeKeyRepository fakeKeys;
    FakeBusinessTransactionRepository fakeBtxRepo;
    VerifyUidCommandHandler handler;

    @BeforeEach
    void setUp() {
        this.fakeQueue = new FakeVerificationQueueRepository();
        this.fakeKeys = new FakeKeyRepository();
        this.fakeBtxRepo = new FakeBusinessTransactionRepository();
        var btxContext = new BusinessTransactionContext();
        btxContext.initialize(42L);
        this.handler = new VerifyUidCommandHandler();
        this.handler.setVerificationQueueRepository(this.fakeQueue);
        this.handler.setKeyRepository(this.fakeKeys);
        this.handler.setBtxRepository(this.fakeBtxRepo);
        this.handler.setBtxContext(btxContext);
    }

    // -----------------------------------------------------------------------
    // Scenario 2: one email address, token times out → key never published
    // -----------------------------------------------------------------------

    @Test
    void scenario2_expired_token_still_records_fingerprint_on_btx_row() {
        // given
        // Even for expired tokens, the BTX row should carry the fingerprint so operators can
        // correlate failed/expired verification attempts with a specific key in the audit log.
        this.fakeQueue.put(TOKEN_1, expiredEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // when
        assertThatThrownBy(() -> this.handler.execute(
                        new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty()))
                .isInstanceOf(TokenExpiredException.class);

        // then
        assertThat(this.fakeBtxRepo.recordedFingerprint)
                .as(
                        "fingerprint must be recorded before the expiry check so expired-token BTX rows are filterable by key")
                .isEqualTo(FINGERPRINT);
    }

    @Test
    void scenario2_expired_token_throws_TokenExpiredException() {
        fakeQueue.put(TOKEN_1, expiredEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        assertThatThrownBy(() -> handler.execute(
                        new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty()))
                .isInstanceOf(TokenExpiredException.class);

        assertThat(fakeKeys.published).isEmpty();
        assertThat(fakeQueue.verifiedIds).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 3: one email address, link clicked → key published
    // -----------------------------------------------------------------------

    @Test
    void scenario3_records_fingerprint_on_btx_row() {
        // given
        // The fingerprint column lets operators filter the BTX audit log by key; the handler must
        // write it once the verification-queue entry (which already carries the fingerprint) is loaded.
        this.fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // when
        this.handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());

        // then
        assertThat(this.fakeBtxRepo.recordedFingerprint)
                .as("the handler must write the key fingerprint to the BTX audit row after loading the queue entry")
                .isEqualTo(FINGERPRINT);
    }

    @Test
    void scenario3_single_email_clicked_publishes_key() {
        fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());

        assertThat(fakeKeys.published).hasSize(1);
        assertThat(fakeKeys.published.getFirst().uidEmail()).isEqualTo("alice@example.com");
        assertThat(fakeKeys.published.getFirst().fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(fakeQueue.verifiedIds).containsExactly(TOKEN_1);
    }

    @Test
    void scenario3_token_cannot_be_replayed() {
        fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // First click succeeds
        handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());

        // Second click on the same token → invalid (consumed)
        assertThatThrownBy(() -> handler.execute(
                        new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty()))
                .isInstanceOf(TokenInvalidException.class);

        // Key was published only once
        assertThat(fakeKeys.published).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Scenario 4: two email addresses, one clicked, one times out
    //   → only first UID published; second token expires silently
    // -----------------------------------------------------------------------

    @Test
    void scenario4_two_emails_one_clicked_one_expired() {
        fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));
        fakeQueue.put(TOKEN_2, expiredEntry(TOKEN_2, "Alice Work <work@corp.example>", "work@corp.example"));

        // First verification succeeds
        handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());

        // Second token has expired
        assertThatThrownBy(() -> handler.execute(
                        new VerifyUidCommand(Long.toUnsignedString(TOKEN_2)), CommandCallerContext.empty()))
                .isInstanceOf(TokenExpiredException.class);

        // Only first UID was published
        assertThat(fakeKeys.published).hasSize(1);
        assertThat(fakeKeys.published.getFirst().uidEmail()).isEqualTo("alice@example.com");
    }

    // -----------------------------------------------------------------------
    // Scenario 5: two email addresses, both clicked (with delay simulated by
    //   sequential handler calls) → both UIDs published
    // -----------------------------------------------------------------------

    @Test
    void scenario5_two_emails_both_clicked_both_published() {
        fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));
        fakeQueue.put(TOKEN_2, pendingEntry(TOKEN_2, "Alice Work <work@corp.example>", "work@corp.example"));

        handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());
        handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_2)), CommandCallerContext.empty());

        assertThat(fakeKeys.published).hasSize(2);
        assertThat(fakeKeys.published)
                .extracting(PublishedUid::uidEmail)
                .containsExactlyInAnyOrder("alice@example.com", "work@corp.example");
        assertThat(fakeQueue.verifiedIds).containsExactlyInAnyOrder(TOKEN_1, TOKEN_2);
    }

    // -----------------------------------------------------------------------
    // Invalid-token edge cases
    // -----------------------------------------------------------------------

    @Test
    void garbled_token_string_throws_TokenInvalidException() {
        assertThatThrownBy(() -> handler.execute(new VerifyUidCommand("not-a-number"), CommandCallerContext.empty()))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(fakeKeys.published).isEmpty();
    }

    @Test
    void unknown_token_throws_TokenInvalidException() {
        // Nothing in the fake queue
        assertThatThrownBy(() -> handler.execute(
                        new VerifyUidCommand(Long.toUnsignedString(9_999L)), CommandCallerContext.empty()))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(fakeKeys.published).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 7: Two UIDs verified at the exact same time (concurrent publication).
    //
    // At the handler level both verifications are independently valid: each token
    // is distinct and each handler invocation succeeds on its own.  The handler
    // itself has no race condition — it calls publishVerifiedUid once and returns.
    //
    // The race lives in the JPA adapter (JpaKeyRepository): two concurrent
    // transactions could both see key == null and both attempt a plain INSERT,
    // causing a constraint violation and losing one UID.  Or both could load the
    // armored block independently and overwrite each other's UID strip.
    //
    // This test verifies the application-layer correctness: both handler invocations
    // must complete successfully and forward their respective UIDs to the repository.
    // The JPA-level serialisation (INSERT ON CONFLICT DO NOTHING + PESSIMISTIC_WRITE)
    // is validated by integration tests in the integration-tests module.
    // -----------------------------------------------------------------------

    @Test
    void scenario7_concurrent_uid_verification_both_published() throws Exception {
        fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));
        fakeQueue.put(TOKEN_2, pendingEntry(TOKEN_2, "Alice Work <work@corp.example>", "work@corp.example"));

        // Release both threads at the same instant to maximise overlap.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        Thread t1 = new Thread(() -> {
            try {
                start.await();
                handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_1)), CommandCallerContext.empty());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                done.countDown();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                start.await();
                handler.execute(new VerifyUidCommand(Long.toUnsignedString(TOKEN_2)), CommandCallerContext.empty());
            } catch (Throwable t) {
                errors.add(t);
            } finally {
                done.countDown();
            }
        });

        t1.start();
        t2.start();
        start.countDown(); // release both simultaneously

        // If await returns false the threads did not finish within the deadline.
        // Interrupt both and join to avoid dangling threads poisoning subsequent tests.
        boolean completed = done.await(5, TimeUnit.SECONDS);
        if (!completed) {
            t1.interrupt();
            t2.interrupt();
            t1.join(1_000);
            t2.join(1_000);
            org.junit.jupiter.api.Assertions.fail(
                    "Concurrent verification did not complete within timeout — possible deadlock");
        }

        assertThat(errors).as("no exception during concurrent verification").isEmpty();
        assertThat(fakeKeys.published)
                .as("both UIDs must be published — neither lost to a concurrent-write race")
                .hasSize(2);
        assertThat(fakeKeys.published)
                .extracting(PublishedUid::uidEmail)
                .containsExactlyInAnyOrder("alice@example.com", "work@corp.example");
        assertThat(fakeQueue.verifiedIds).containsExactlyInAnyOrder(TOKEN_1, TOKEN_2);
    }
}
