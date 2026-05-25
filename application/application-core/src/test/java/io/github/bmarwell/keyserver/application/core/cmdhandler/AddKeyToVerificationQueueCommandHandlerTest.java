/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.application.api.ex.TooManyVerifiableUidsException;
import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddKeyToVerificationQueueCommandHandlerTest {

    /** Fake repository that records every enqueue call and returns sequential IDs. */
    static class FakeVerificationQueueRepository implements VerificationQueueRepository {
        final List<VerificationRequest> received = new ArrayList<>();
        private final AtomicLong counter = new AtomicLong(1_000L);

        @Override
        public long enqueue(VerificationRequest request) {
            this.received.add(request);
            return this.counter.getAndIncrement();
        }

        @Override
        public Optional<VerificationEntry> findPendingById(long tokenId) {
            return Optional.empty();
        }

        @Override
        public void markVerified(long tokenId) {}
    }

    /** Fake notification port that records every call. */
    static class FakeNotificationPort implements VerificationNotificationPort {
        record Notification(String email, String fingerprint, URI verificationUri) {}

        final List<Notification> received = new ArrayList<>();

        @Override
        public void notifyPendingVerification(String toEmail, String fingerprint, URI verificationUri) {
            this.received.add(new Notification(toEmail, fingerprint, verificationUri));
        }
    }

    FakeVerificationQueueRepository fakeRepo;
    FakeNotificationPort fakeNotification;
    AddKeyToVerificationQueueCommandHandler handler;

    @BeforeEach
    void setUp() {
        this.fakeRepo = new FakeVerificationQueueRepository();
        this.fakeNotification = new FakeNotificationPort();
        this.handler = new AddKeyToVerificationQueueCommandHandler();
        this.handler.setVerificationQueueRepository(this.fakeRepo);
        this.handler.setNotificationPort(this.fakeNotification);
    }

    private String loadTestKey(String resourceName) throws IOException {
        try (var stream = getClass().getResourceAsStream("/pgp/" + resourceName)) {
            assertThat(stream)
                    .as("test resource /pgp/%s must exist", resourceName)
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void happy_path_enqueues_one_entry_per_uid() throws IOException {
        String keyText = loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText);

        this.handler.doExecute(command, CommandCallerContext.empty());

        // The test key has one UID: "Test Key <testkey@example.com>"
        assertThat(this.fakeRepo.received).hasSize(1);
        assertThat(this.fakeRepo.received.getFirst().uidEmail()).isEqualTo("testkey@example.com");
        assertThat(this.fakeRepo.received.getFirst().fingerprint()).isNotBlank();
    }

    @Test
    void happy_path_notifies_once_per_uid() throws IOException {
        String keyText = loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText);

        this.handler.doExecute(command, CommandCallerContext.empty());

        assertThat(this.fakeNotification.received).hasSize(1);
        FakeNotificationPort.Notification note = this.fakeNotification.received.getFirst();
        assertThat(note.email()).isEqualTo("testkey@example.com");
        assertThat(note.verificationUri().toString()).startsWith("/verify/");
    }

    @Test
    void rejects_null_key_text() {
        var command = new AddKeyToVerificationQueueCommand(null);

        assertThatThrownBy(() -> this.handler.doExecute(command, CommandCallerContext.empty()))
                .isInstanceOf(KeyParsingException.class);
        assertThat(this.fakeRepo.received).isEmpty();
    }

    @Test
    void rejects_blank_key_text() {
        var command = new AddKeyToVerificationQueueCommand("   ");

        assertThatThrownBy(() -> this.handler.doExecute(command, CommandCallerContext.empty()))
                .isInstanceOf(KeyParsingException.class);
        assertThat(this.fakeRepo.received).isEmpty();
    }

    @Test
    void rejects_garbage_key_text() {
        var command = new AddKeyToVerificationQueueCommand("not a pgp key");

        assertThatThrownBy(() -> this.handler.doExecute(command, CommandCallerContext.empty()))
                .isInstanceOf(KeyParsingException.class);
        assertThat(this.fakeRepo.received).isEmpty();
        assertThat(this.fakeNotification.received).isEmpty();
    }

    @Test
    void accepts_key_text_within_configured_byte_limit() throws IOException {
        // given
        String keyText = this.loadTestKey("test-key-with-email.asc");
        int keySizeBytes = keyText.getBytes(StandardCharsets.UTF_8).length;
        this.handler.setMaxKeyBytes(keySizeBytes);
        var command = new AddKeyToVerificationQueueCommand(keyText);

        // when
        this.handler.doExecute(command, CommandCallerContext.empty());

        // then
        assertThat(this.fakeRepo.received).hasSize(1);
        assertThat(this.fakeNotification.received).hasSize(1);
    }

    @Test
    void rejects_key_text_larger_than_configured_byte_limit() throws IOException {
        // given
        String keyText = this.loadTestKey("test-key-with-email.asc");
        int keySizeBytes = keyText.getBytes(StandardCharsets.UTF_8).length;
        this.handler.setMaxKeyBytes(keySizeBytes - 1);
        var command = new AddKeyToVerificationQueueCommand(keyText);

        // when
        Throwable thrown = catchThrowable(() -> this.handler.doExecute(command, CommandCallerContext.empty()));

        // then
        assertThat(thrown).isInstanceOf(KeyParsingException.class).hasMessageContaining("maximum allowed size");
        assertThat(this.fakeRepo.received).isEmpty();
        assertThat(this.fakeNotification.received).isEmpty();
    }

    @Test
    void non_positive_configured_byte_limit_falls_back_to_default_limit() {
        String oversizedAsciiKey = "a".repeat(AddKeyToVerificationQueueCommandHandler.DEFAULT_MAX_KEY_BYTES + 1);

        for (int configuredLimit : List.of(0, -1)) {
            this.handler.setMaxKeyBytes(configuredLimit);

            assertThatThrownBy(() -> this.handler.doExecute(
                            new AddKeyToVerificationQueueCommand(oversizedAsciiKey), CommandCallerContext.empty()))
                    .isInstanceOf(KeyParsingException.class)
                    .hasMessageContaining("maximum allowed size");
        }

        assertThat(this.fakeRepo.received).isEmpty();
        assertThat(this.fakeNotification.received).isEmpty();
    }

    @Test
    void verification_uri_contains_tsid_of_enqueued_entry() throws IOException {
        String keyText = this.loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText);

        this.handler.doExecute(command, CommandCallerContext.empty());

        String expectedTsidStr = Long.toUnsignedString(1000L); // first ID assigned by fake repo
        assertThat(this.fakeNotification.received.getFirst().verificationUri().toString())
                .endsWith(expectedTsidStr);
    }

    // -----------------------------------------------------------------------
    // Scenario 6: one million (well, MAX_EMAIL_UIDS + 1) email UIDs → rejected
    // -----------------------------------------------------------------------

    /**
     * Subclass that overrides {@code collectEmailUids} to return a synthetic list
     * of fake email addresses, bypassing the need for a real PGP key with many UIDs.
     * This tests the DoS guard in isolation from the PGP parsing logic.
     */
    static class OverflowingHandler extends AddKeyToVerificationQueueCommandHandler {
        private final int uidCount;

        OverflowingHandler(int uidCount) {
            this.uidCount = uidCount;
        }

        @Override
        List<String> collectEmailUids(PGPPublicKey masterKey) {
            return IntStream.range(0, uidCount)
                    .mapToObj(i -> "user%d@example.com".formatted(i))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    @Test
    void scenario6_too_many_email_uids_rejected() throws IOException {
        int overLimit = AddKeyToVerificationQueueCommandHandler.MAX_EMAIL_UIDS + 1;
        var overflowHandler = new OverflowingHandler(overLimit);
        overflowHandler.setVerificationQueueRepository(this.fakeRepo);
        overflowHandler.setNotificationPort(this.fakeNotification);

        String keyText = this.loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText);

        assertThatThrownBy(() -> overflowHandler.doExecute(command, CommandCallerContext.empty()))
                .isInstanceOf(TooManyVerifiableUidsException.class)
                .hasMessageContaining(String.valueOf(overLimit))
                .hasMessageContaining(String.valueOf(AddKeyToVerificationQueueCommandHandler.MAX_EMAIL_UIDS));

        assertThat(this.fakeRepo.received).isEmpty();
        assertThat(this.fakeNotification.received).isEmpty();
    }
}
