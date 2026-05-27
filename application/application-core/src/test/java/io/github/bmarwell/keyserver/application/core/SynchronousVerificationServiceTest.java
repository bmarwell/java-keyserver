/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Unit tests for {@link SynchronousVerificationService}.
///
/// All major verification-flow scenarios are covered here without a CDI container or
/// database. Dependencies are injected via CDI-friendly setters using in-memory fakes.
class SynchronousVerificationServiceTest {

    // -----------------------------------------------------------------------
    // Fakes
    // -----------------------------------------------------------------------

    record PublishedUid(String fingerprint, String uidRaw, String uidEmail) {}

    static class FakeKeyRepository implements KeyRepository {
        final List<PublishedUid> published = new ArrayList<>();

        @Override
        public void publishVerifiedUid(String fingerprint, String uidRaw, String uidEmail, String armoredKey) {
            this.published.add(new PublishedUid(fingerprint, uidRaw, uidEmail));
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

    static class FakeVerificationQueueRepository implements VerificationQueueRepository {

        record StoredEntry(VerificationEntry entry, boolean consumed) {}

        final Map<Long, StoredEntry> store = Collections.synchronizedMap(new HashMap<>());
        final List<Long> verifiedIds = Collections.synchronizedList(new ArrayList<>());

        void put(long id, VerificationEntry entry) {
            this.store.put(id, new StoredEntry(entry, false));
        }

        @Override
        public long enqueue(VerificationRequest request) {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public Optional<VerificationEntry> findPendingById(long tokenId) {
            StoredEntry stored = this.store.get(tokenId);
            if (stored == null || stored.consumed()) {
                return Optional.empty();
            }
            return Optional.of(stored.entry());
        }

        @Override
        public void markVerified(long tokenId) {
            StoredEntry stored = this.store.get(tokenId);
            if (stored != null) {
                this.store.put(tokenId, new StoredEntry(stored.entry(), true));
                this.verifiedIds.add(tokenId);
            }
        }
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
    SynchronousVerificationService service;

    @BeforeEach
    void setUp() {
        this.fakeQueue = new FakeVerificationQueueRepository();
        this.fakeKeys = new FakeKeyRepository();
        this.service = new SynchronousVerificationService();
        this.service.setVerificationQueueRepository(this.fakeQueue);
        this.service.setKeyRepository(this.fakeKeys);
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void valid_token_publishes_uid_and_returns_result() {
        // given
        this.fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // when
        VerificationResult result = this.service.verifyUid(Long.toUnsignedString(TOKEN_1));

        // then
        assertThat(result.uidRaw()).isEqualTo("Alice <alice@example.com>");
        assertThat(result.fingerprint()).isEqualTo(FINGERPRINT);
        assertThat(this.fakeKeys.published).hasSize(1);
        assertThat(this.fakeKeys.published.getFirst().uidEmail()).isEqualTo("alice@example.com");
        assertThat(this.fakeQueue.verifiedIds).containsExactly(TOKEN_1);
    }

    @Test
    void token_is_consumed_after_first_use() {
        // given
        this.fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // when
        this.service.verifyUid(Long.toUnsignedString(TOKEN_1));

        // then: second use of the same token must be rejected
        assertThatThrownBy(() -> this.service.verifyUid(Long.toUnsignedString(TOKEN_1)))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(this.fakeKeys.published).hasSize(1);
    }

    @Test
    void two_separate_tokens_both_verified() {
        // given
        this.fakeQueue.put(TOKEN_1, pendingEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));
        this.fakeQueue.put(TOKEN_2, pendingEntry(TOKEN_2, "Alice Work <work@corp.example>", "work@corp.example"));

        // when
        this.service.verifyUid(Long.toUnsignedString(TOKEN_1));
        this.service.verifyUid(Long.toUnsignedString(TOKEN_2));

        // then
        assertThat(this.fakeKeys.published)
                .extracting(PublishedUid::uidEmail)
                .containsExactlyInAnyOrder("alice@example.com", "work@corp.example");
        assertThat(this.fakeQueue.verifiedIds).containsExactlyInAnyOrder(TOKEN_1, TOKEN_2);
    }

    // -----------------------------------------------------------------------
    // Error cases
    // -----------------------------------------------------------------------

    @Test
    void expired_token_throws_TokenExpiredException() {
        // given
        this.fakeQueue.put(TOKEN_1, expiredEntry(TOKEN_1, "Alice <alice@example.com>", "alice@example.com"));

        // when / then
        assertThatThrownBy(() -> this.service.verifyUid(Long.toUnsignedString(TOKEN_1)))
                .isInstanceOf(TokenExpiredException.class);
        assertThat(this.fakeKeys.published).isEmpty();
        assertThat(this.fakeQueue.verifiedIds).isEmpty();
    }

    @Test
    void unknown_token_throws_TokenInvalidException() {
        // given — nothing in the queue

        // when / then
        assertThatThrownBy(() -> this.service.verifyUid(Long.toUnsignedString(9_999L)))
                .isInstanceOf(TokenInvalidException.class);
        assertThat(this.fakeKeys.published).isEmpty();
    }

    @Test
    void garbled_token_string_throws_TokenInvalidException() {
        // given — token is not a valid unsigned integer

        // when / then
        assertThatThrownBy(() -> this.service.verifyUid("not-a-number")).isInstanceOf(TokenInvalidException.class);
        assertThat(this.fakeKeys.published).isEmpty();
    }

    @Test
    void negative_token_string_throws_TokenInvalidException() {
        // given — negative numbers are not valid unsigned long strings

        // when / then
        assertThatThrownBy(() -> this.service.verifyUid("-1")).isInstanceOf(TokenInvalidException.class);
    }
}
