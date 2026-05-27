/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.CommandVerificationRegistry;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.KeySubmissionVerifier;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Handles the {@link AddKeyToVerificationQueueCommand}.
///
/// ## What this handler does
///
/// 1. Verifies and prepares the ASCII-armored key submission.
/// 2. Enqueues one {@link VerificationRequest} per surviving UID via the
///    {@link VerificationQueueRepository} port.  The TSID returned by the
///    adapter is used to construct the verification URI.
/// 3. Notifies via {@link VerificationNotificationPort} (first iteration: logs
///    the URI; later: sends email).
///
/// ## Expiry
///
/// Tokens expire after {@value #TOKEN_TTL_HOURS} hours by default.
///
/// ## Caller context
///
/// The `CommandCallerContext` carries the pre-anonymized caller IP for audit
/// purposes.  This handler does not need the IP directly — it was already
/// forwarded to the BTX audit row by `KeyServerCommandService`.  The parameter
/// is present to satisfy the handler contract (see implementation-plan §8.7).
@RequestScoped
public class AddKeyToVerificationQueueCommandHandler
        extends AbstractKeyServerCommandHandler<
                AddKeyToVerificationQueueCommand, KeySubmissionVerifier.VerifiedKeySubmission> {

    static final int TOKEN_TTL_HOURS = 24;
    static final int DEFAULT_MAX_KEY_BYTES = 128 * 1024;
    static final int MAX_SUBKEYS = 50;
    static final String MAX_KEY_BYTES_CONFIG_KEY = "keyserver.pks.max-key-bytes";

    /// Maximum number of email-bearing UIDs accepted from a single key submission.
    ///
    /// ## Rationale
    ///
    /// The 2019 SKS keyserver poisoning attack used keys with tens of thousands of
    /// UIDs to exhaust memory and CPU in anything that processed them.  keys.openpgp.org
    /// responded by capping at 5 verified email UIDs.
    ///
    /// This implementation uses 20:
    /// - Legitimate real-world keys carry 1–3 UIDs (personal, work, alias).
    /// - Power users with many email identities rarely exceed 10.
    /// - 20 leaves ample headroom while remaining 50 000× below DoS territory.
    /// - Each UID triggers one outbound email and one user interaction, so the cap
    ///   also limits resource consumption during the verification flow itself.
    ///
    static final int MAX_EMAIL_UIDS = 20;

    @Inject
    VerificationQueueRepository verificationQueueRepository;

    @Inject
    VerificationNotificationPort notificationPort;

    @Inject
    BusinessTransactionRepository btxRepository;

    @Inject
    BusinessTransactionContext btxContext;

    @Inject
    @ConfigProperty(name = MAX_KEY_BYTES_CONFIG_KEY, defaultValue = "" + DEFAULT_MAX_KEY_BYTES)
    int maxKeyBytes;

    private CommandVerificationRegistry<AddKeyToVerificationQueueCommand, KeySubmissionVerifier.VerifiedKeySubmission>
            keySubmissionVerifier = this.newKeySubmissionVerifier(DEFAULT_MAX_KEY_BYTES);
    private boolean useCustomKeySubmissionVerifier;

    @Override
    public <C extends KeyServerCommand> boolean canHandle(C command) {
        return command instanceof AddKeyToVerificationQueueCommand;
    }

    @Override
    protected CommandVerificationRegistry<AddKeyToVerificationQueueCommand, KeySubmissionVerifier.VerifiedKeySubmission>
            verificationRegistry() {
        if (this.useCustomKeySubmissionVerifier) {
            return this.keySubmissionVerifier;
        }
        return this.newKeySubmissionVerifier(this.maxKeyBytes);
    }

    @Override
    KeyServerCommandResponse doExecute(
            AddKeyToVerificationQueueCommand command,
            KeySubmissionVerifier.VerifiedKeySubmission verification,
            CommandCallerContext callerContext) {
        // Record the fingerprint on the BTX row when all identities share the same primary key.
        // If identities from multiple distinct keys were submitted we skip fingerprint recording;
        // picking the first arbitrarily would make the BTX row misleading.
        List<String> distinctFingerprints = verification.verifiedIdentities().stream()
                .map(KeySubmissionVerifier.VerifiedKeyIdentity::fingerprint)
                .distinct()
                .toList();
        if (distinctFingerprints.size() == 1) {
            this.btxRepository.recordFingerprint(this.btxContext.getBtxId(), distinctFingerprints.getFirst());
        }
        this.enqueueVerificationRequests(verification);

        return KeyServerCommandResponse.success();
    }

    private void enqueueVerificationRequests(KeySubmissionVerifier.VerifiedKeySubmission verifiedSubmission) {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(TOKEN_TTL_HOURS);
        for (KeySubmissionVerifier.VerifiedKeyIdentity verifiedIdentity : verifiedSubmission.verifiedIdentities()) {
            var request = new VerificationRequest(
                    verifiedIdentity.fingerprint(),
                    verifiedIdentity.uidRaw(),
                    verifiedIdentity.uidEmail(),
                    verifiedSubmission.armoredKey(),
                    expiresAt);
            long tsid = this.verificationQueueRepository.enqueue(request);
            URI verificationUri = this.buildVerificationUri(tsid);
            this.notificationPort.notifyPendingVerification(
                    verifiedIdentity.uidEmail(), verifiedIdentity.fingerprint(), verificationUri);
        }
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

    public void setBtxRepository(BusinessTransactionRepository btxRepository) {
        this.btxRepository = btxRepository;
    }

    public void setBtxContext(BusinessTransactionContext btxContext) {
        this.btxContext = btxContext;
    }

    void setKeySubmissionVerifier(
            CommandVerificationRegistry<AddKeyToVerificationQueueCommand, KeySubmissionVerifier.VerifiedKeySubmission>
                    keySubmissionVerifier) {
        this.keySubmissionVerifier = keySubmissionVerifier;
        this.useCustomKeySubmissionVerifier = true;
    }

    void setMaxKeyBytes(int maxKeyBytes) {
        this.maxKeyBytes = maxKeyBytes;
    }

    private KeySubmissionVerifier newKeySubmissionVerifier(int configuredMaxKeyBytes) {
        return new KeySubmissionVerifier(configuredMaxKeyBytes, DEFAULT_MAX_KEY_BYTES, MAX_EMAIL_UIDS, MAX_SUBKEYS);
    }
}
