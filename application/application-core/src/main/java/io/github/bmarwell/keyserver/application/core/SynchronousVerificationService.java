/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import io.github.bmarwell.keyserver.application.api.VerificationService;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
import io.github.bmarwell.keyserver.application.port.repository.KeyRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;

/// Synchronous implementation of {@link VerificationService}.
///
/// Unlike the async command-handler path ({@link
/// io.github.bmarwell.keyserver.application.core.cmdhandler.VerifyUidCommandHandler}
/// via {@link io.github.bmarwell.keyserver.application.api.CommandService}),
/// this service runs the full verification flow synchronously inside a single JTA
/// transaction and surfaces the outcome to the caller immediately.
///
/// This service does not write to the business-transaction (BTX) audit table — BTX
/// is an artefact of the async command pipeline and is not available in the sync path.
///
/// This is the preferred path for any endpoint that must confirm verification success
/// or failure to the user inline (e.g. the HKP {@code /pks/verify/{token}} endpoint
/// and future VKS endpoints).
@ApplicationScoped
public class SynchronousVerificationService implements VerificationService {

    @Inject
    VerificationQueueRepository verificationQueueRepository;

    @Inject
    KeyRepository keyRepository;

    @Override
    @Transactional
    public VerificationResult verifyUid(String token) {
        long tokenId = parseToken(token);

        VerificationEntry entry = this.verificationQueueRepository
                .findPendingById(tokenId)
                .orElseThrow(() -> new TokenInvalidException(
                        // Do not echo the token value — it is attacker-controlled and could
                        // be arbitrarily long or contain control characters (log injection).
                        "Verification token not found or already consumed"));

        if (entry.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new TokenExpiredException("Verification token has expired");
        }

        this.verificationQueueRepository.markVerified(tokenId);
        this.keyRepository.publishVerifiedUid(
                entry.fingerprint(), entry.uidRaw(), entry.uidEmail(), entry.armoredKey());

        return new VerificationResult(entry.uidRaw(), entry.fingerprint());
    }

    private static long parseToken(String token) {
        try {
            return Long.parseUnsignedLong(token);
        } catch (NumberFormatException e) {
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
}
