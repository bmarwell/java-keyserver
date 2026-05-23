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
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddKeyToVerificationQueueCommandHandlerTest {

    /** Fake repository that records every enqueue call and returns sequential IDs. */
    static class FakeVerificationQueueRepository implements VerificationQueueRepository {
        final List<VerificationRequest> received = new ArrayList<>();
        private final AtomicLong counter = new AtomicLong(1_000L);

        @Override
        public long enqueue(VerificationRequest request) {
            received.add(request);
            return counter.getAndIncrement();
        }
    }

    /** Fake notification port that records every call. */
    static class FakeNotificationPort implements VerificationNotificationPort {
        record Notification(String email, String fingerprint, URI verificationUri) {}

        final List<Notification> received = new ArrayList<>();

        @Override
        public void notifyPendingVerification(String toEmail, String fingerprint, URI verificationUri) {
            received.add(new Notification(toEmail, fingerprint, verificationUri));
        }
    }

    FakeVerificationQueueRepository fakeRepo;
    FakeNotificationPort fakeNotification;
    AddKeyToVerificationQueueCommandHandler handler;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeVerificationQueueRepository();
        fakeNotification = new FakeNotificationPort();
        handler = new AddKeyToVerificationQueueCommandHandler();
        handler.setVerificationQueueRepository(fakeRepo);
        handler.setNotificationPort(fakeNotification);
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
        var command = new AddKeyToVerificationQueueCommand(keyText, "192.168.1.0");

        handler.doExecute(command);

        // The test key has one UID: "Test Key <testkey@example.com>"
        assertThat(fakeRepo.received).hasSize(1);
        assertThat(fakeRepo.received.getFirst().uidEmail()).isEqualTo("testkey@example.com");
        assertThat(fakeRepo.received.getFirst().fingerprint()).isNotBlank();
    }

    @Test
    void happy_path_notifies_once_per_uid() throws IOException {
        String keyText = loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText, "10.0.0.0");

        handler.doExecute(command);

        assertThat(fakeNotification.received).hasSize(1);
        FakeNotificationPort.Notification note = fakeNotification.received.getFirst();
        assertThat(note.email()).isEqualTo("testkey@example.com");
        assertThat(note.verificationUri().toString()).startsWith("/verify/");
    }

    @Test
    void rejects_null_key_text() {
        var command = new AddKeyToVerificationQueueCommand(null, "10.0.0.0");

        assertThatThrownBy(() -> handler.doExecute(command)).isInstanceOf(KeyParsingException.class);
        assertThat(fakeRepo.received).isEmpty();
    }

    @Test
    void rejects_blank_key_text() {
        var command = new AddKeyToVerificationQueueCommand("   ", "10.0.0.0");

        assertThatThrownBy(() -> handler.doExecute(command)).isInstanceOf(KeyParsingException.class);
        assertThat(fakeRepo.received).isEmpty();
    }

    @Test
    void rejects_garbage_key_text() {
        var command = new AddKeyToVerificationQueueCommand("not a pgp key", "10.0.0.0");

        assertThatThrownBy(() -> handler.doExecute(command)).isInstanceOf(KeyParsingException.class);
        assertThat(fakeRepo.received).isEmpty();
        assertThat(fakeNotification.received).isEmpty();
    }

    @Test
    void verification_uri_contains_tsid_of_enqueued_entry() throws IOException {
        String keyText = loadTestKey("test-key-with-email.asc");
        var command = new AddKeyToVerificationQueueCommand(keyText, "10.0.0.0");

        handler.doExecute(command);

        String expectedTsidStr = Long.toUnsignedString(1000L); // first ID assigned by fake repo
        assertThat(fakeNotification.received.getFirst().verificationUri().toString())
                .endsWith(expectedTsidStr);
    }
}
