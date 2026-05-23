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

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.application.api.ex.KeyRevokedException;
import io.github.bmarwell.keyserver.application.api.ex.NoVerifiableUidException;
import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

/// Handles the {@link AddKeyToVerificationQueueCommand}.
///
/// ## What this handler does
///
/// 1. Parses the ASCII-armored key text with Bouncycastle.
/// 2. For each key ring in the submission, locates the master key.
/// 3. Rejects keys that are revoked (has a direct-key revocation signature).
/// 4. Collects only the UIDs that contain an RFC-5322 email address (the
///    `<addr>` part inside angle brackets, or the bare address if no angle
///    brackets are present).
/// 5. Throws {@link NoVerifiableUidException} if no email-bearing UID survives.
/// 6. Enqueues one {@link VerificationRequest} per surviving UID via the
///    {@link VerificationQueueRepository} port.  The TSID returned by the
///    adapter is used to construct the verification URI.
/// 7. Notifies via {@link VerificationNotificationPort} (first iteration: logs
///    the URI; later: sends email).
///
/// ## Expiry
///
/// Tokens expire after {@value #TOKEN_TTL_HOURS} hours by default.  A future
/// iteration will make this configurable via MicroProfile Config.
@RequestScoped
public class AddKeyToVerificationQueueCommandHandler
        extends AbstractKeyServerCommandHandler<AddKeyToVerificationQueueCommand> {

    static final int TOKEN_TTL_HOURS = 24;

    @Inject
    VerificationQueueRepository verificationQueueRepository;

    @Inject
    VerificationNotificationPort notificationPort;

    @Override
    public <C extends KeyServerCommand> boolean canHandle(C command) {
        return command instanceof AddKeyToVerificationQueueCommand;
    }

    @Override
    KeyServerCommandResponse doExecute(AddKeyToVerificationQueueCommand command) {
        PGPPublicKeyRingCollection keyRingCollection = parseKeyText(command.keyText());

        if (!keyRingCollection.iterator().hasNext()) {
            throw new KeyParsingException("Key text contains no valid OpenPGP key rings");
        }

        for (Iterator<PGPPublicKeyRing> rings = keyRingCollection.getKeyRings(); rings.hasNext(); ) {
            PGPPublicKeyRing keyRing = rings.next();
            processKeyRing(keyRing, command.keyText());
        }

        return KeyServerCommandResponse.success();
    }

    private void processKeyRing(PGPPublicKeyRing keyRing, String armoredKey) {
        PGPPublicKey masterKey = keyRing.getPublicKey();

        if (masterKey.hasRevocation()) {
            throw new KeyRevokedException(
                    "Master key %s is revoked".formatted(fingerprintHex(masterKey)), () -> fingerprintHex(masterKey));
        }

        List<String> emailUids = collectEmailUids(masterKey);
        if (emailUids.isEmpty()) {
            throw new NoVerifiableUidException(
                    "Key %s has no UIDs with a verifiable email address".formatted(fingerprintHex(masterKey)));
        }

        String fingerprint = fingerprintHex(masterKey);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS);

        for (String uid : emailUids) {
            String email = extractEmail(uid);
            var request = new VerificationRequest(fingerprint, uid, email, armoredKey, expiresAt);
            long tsid = verificationQueueRepository.enqueue(request);
            URI verificationUri = buildVerificationUri(tsid);
            notificationPort.notifyPendingVerification(email, fingerprint, verificationUri);
        }
    }

    private PGPPublicKeyRingCollection parseKeyText(String keyText) {
        try {
            byte[] keyBytes = keyText.getBytes(StandardCharsets.UTF_8);
            try (var decoderStream = PGPUtil.getDecoderStream(new ByteArrayInputStream(keyBytes))) {
                return new PGPPublicKeyRingCollection(decoderStream, new BcKeyFingerprintCalculator());
            }
        } catch (IOException | PGPException e) {
            throw new KeyParsingException("Failed to parse PGP key: " + e.getMessage(), e);
        }
    }

    private List<String> collectEmailUids(PGPPublicKey masterKey) {
        List<String> emailUids = new ArrayList<>();
        for (Iterator<String> userIds = masterKey.getUserIDs(); userIds.hasNext(); ) {
            String uid = userIds.next();
            if (containsEmail(uid)) {
                emailUids.add(uid);
            }
        }
        return emailUids;
    }

    private boolean containsEmail(String uid) {
        return extractEmail(uid) != null;
    }

    /// Extracts the email address from a UID string.
    ///
    /// Supports both `Name <email@example.com>` and bare `email@example.com` forms.
    /// Returns `null` if no valid email pattern is found.
    private String extractEmail(String uid) {
        if (uid == null || uid.isBlank()) {
            return null;
        }
        int lt = uid.indexOf('<');
        int gt = uid.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String candidate = uid.substring(lt + 1, gt).strip();
            return isValidEmail(candidate) ? candidate : null;
        }
        // bare address
        return isValidEmail(uid.strip()) ? uid.strip() : null;
    }

    private boolean isValidEmail(String candidate) {
        int at = candidate.indexOf('@');
        return at > 0 && at < candidate.length() - 1 && !candidate.contains(" ");
    }

    private String fingerprintHex(PGPPublicKey key) {
        return HexFormat.of().formatHex(key.getFingerprint()).toUpperCase();
    }

    /// Builds the verification URI from a TSID token.
    ///
    /// The URI path is `/verify/{tsid}`.  In a full deployment the base URL
    /// would come from MicroProfile Config; here we use a relative URI so the
    /// handler stays infrastructure-free.
    private URI buildVerificationUri(long tsid) {
        return URI.create("/verify/" + Long.toUnsignedString(tsid));
    }

    public void setVerificationQueueRepository(VerificationQueueRepository verificationQueueRepository) {
        this.verificationQueueRepository = verificationQueueRepository;
    }

    public void setNotificationPort(VerificationNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }
}
