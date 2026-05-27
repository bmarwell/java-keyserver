/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.api.commands.VerifyUidCommand;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.CommandVerificationRegistry;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.NoCommandVerification;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.NoOpCommandVerificationRegistry;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationEntry;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;

/// Handles {@link VerifyUidCommand} — the action triggered when an owner clicks
/// the email verification link.
///
/// ## Flow
///
/// 1. Parse the token string as an unsigned-decimal `long` (the TSID embedded in
///    the `/verify/{token}` URI).  An unparseable token → {@link TokenInvalidException}.
/// 2. Look up the verification-queue entry with {@link VerificationQueueRepository#findPendingById}.
///    Returns empty for both "not found" and "already consumed" cases — both map to
///    {@link TokenInvalidException} to avoid leaking whether a token ever existed.
/// 3. Check `expiresAt`: if the token has passed its deadline → {@link TokenExpiredException}.
/// 4. Mark the entry {@code VERIFIED} so it cannot be replayed.
/// 5. Publish the verified UID via {@link KeyRepository#publishVerifiedUid}: creates the
///    key row on first verification, appends the UID on subsequent ones (partial
///    publishing — a key may be live with one UID while another is still pending).
///
/// ## Caller context
///
/// The `CommandCallerContext` is threaded through for audit consistency but this
/// handler does not require the IP directly.
@RequestScoped
public class VerifyUidCommandHandler extends AbstractKeyServerCommandHandler<VerifyUidCommand, NoCommandVerification> {

    private final CommandVerificationRegistry<VerifyUidCommand, NoCommandVerification> noOpVerificationRegistry =
            new NoOpCommandVerificationRegistry<>();

    @Inject
    VerificationQueueRepository verificationQueueRepository;

    @Inject
    KeyRepository keyRepository;

    @Inject
    BusinessTransactionRepository btxRepository;

    @Inject
    BusinessTransactionContext btxContext;

    @Override
    public <C extends KeyServerCommand> boolean canHandle(C command) {
        return command instanceof VerifyUidCommand;
    }

    @Override
    protected CommandVerificationRegistry<VerifyUidCommand, NoCommandVerification> verificationRegistry() {
        return this.noOpVerificationRegistry;
    }

    @Override
    KeyServerCommandResponse doExecute(
            VerifyUidCommand command, NoCommandVerification verification, CommandCallerContext callerContext) {
        long tokenId = this.parseToken(command.token());

        VerificationEntry entry = this.verificationQueueRepository
                .findPendingById(tokenId)
                .orElseThrow(() -> new TokenInvalidException(
                        // Do not echo the token value into the exception message.
                        // The message is logged by the exception mapper, and including the token
                        // would: (a) expose a credential-like value in logs, and (b) enable log
                        // injection if a caller passes an extremely long or control-char-bearing string.
                        "Verification token not found or already consumed"));

        if (entry.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new TokenExpiredException("Verification token has expired for fingerprint " + entry.fingerprint());
        }

        this.verificationQueueRepository.markVerified(tokenId);
        this.btxRepository.recordFingerprint(this.btxContext.getBtxId(), entry.fingerprint());
        this.keyRepository.publishVerifiedUid(
                entry.fingerprint(), entry.uidRaw(), entry.uidEmail(), entry.armoredKey());

        return KeyServerCommandResponse.success();
    }

    private long parseToken(String token) {
        try {
            return Long.parseUnsignedLong(token);
        } catch (NumberFormatException e) {
            // Do not include the raw token in the message — it is attacker-controlled
            // and could be arbitrarily long or contain control characters (log injection).
            throw new TokenInvalidException("Verification token is not a valid unsigned integer");
        }
    }

    // CDI-friendly setters for unit testing
    public void setVerificationQueueRepository(VerificationQueueRepository verificationQueueRepository) {
        this.verificationQueueRepository = verificationQueueRepository;
    }

    public void setKeyRepository(KeyRepository keyRepository) {
        this.keyRepository = keyRepository;
    }

    public void setBtxRepository(BusinessTransactionRepository btxRepository) {
        this.btxRepository = btxRepository;
    }

    public void setBtxContext(BusinessTransactionContext btxContext) {
        this.btxContext = btxContext;
    }
}
