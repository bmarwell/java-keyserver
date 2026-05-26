/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;

/// Runs the add-path verification chain for an armored key submission.
public class KeySubmissionVerifier
        implements CommandVerificationRegistry<
                AddKeyToVerificationQueueCommand, KeySubmissionVerifier.VerifiedKeySubmission> {

    private final int configuredMaxKeyBytes;
    private final int defaultMaxKeyBytes;
    private final VerificationStep<KeyTextSizeValidator.Input, byte[]> keyTextSizeValidator;
    private final VerificationStep<byte[], PGPPublicKeyRingCollection> keyTextParser;
    private final VerificationStep<VerifiedKeyMaterial, VerifiedKeySubmission> keyRingVerifier;

    public KeySubmissionVerifier(int configuredMaxKeyBytes, int defaultMaxKeyBytes, int maxEmailUids, int maxSubkeys) {
        this(
                configuredMaxKeyBytes,
                defaultMaxKeyBytes,
                new KeyTextSizeValidator(),
                new KeyTextParser(),
                new KeyRingVerifier(maxEmailUids, maxSubkeys));
    }

    KeySubmissionVerifier(
            int configuredMaxKeyBytes,
            int defaultMaxKeyBytes,
            VerificationStep<KeyTextSizeValidator.Input, byte[]> keyTextSizeValidator,
            VerificationStep<byte[], PGPPublicKeyRingCollection> keyTextParser,
            VerificationStep<VerifiedKeyMaterial, VerifiedKeySubmission> keyRingVerifier) {
        this.configuredMaxKeyBytes = configuredMaxKeyBytes;
        this.defaultMaxKeyBytes = defaultMaxKeyBytes;
        this.keyTextSizeValidator = keyTextSizeValidator;
        this.keyTextParser = keyTextParser;
        this.keyRingVerifier = keyRingVerifier;
    }

    @Override
    public VerifiedKeySubmission verify(AddKeyToVerificationQueueCommand command, CommandCallerContext callerContext) {
        byte[] keyTextBytes = this.keyTextSizeValidator.verify(
                new KeyTextSizeValidator.Input(command.keyText(), this.configuredMaxKeyBytes, this.defaultMaxKeyBytes));
        String armoredKey = Objects.requireNonNull(command.keyText(), "Verified key text must be non-null");
        PGPPublicKeyRingCollection keyRingCollection = this.keyTextParser.verify(keyTextBytes);

        if (!keyRingCollection.iterator().hasNext()) {
            throw new KeyParsingException("Key text contains no valid OpenPGP key rings");
        }

        return this.keyRingVerifier.verify(new VerifiedKeyMaterial(armoredKey, keyRingCollection));
    }

    public static Optional<String> extractEmail(String uid) {
        if (uid == null || uid.isBlank()) {
            return Optional.empty();
        }
        int lt = uid.indexOf('<');
        int gt = uid.indexOf('>');
        if (lt >= 0 && gt > lt) {
            String candidate = uid.substring(lt + 1, gt).strip();
            return isValidEmail(candidate) ? Optional.of(candidate) : Optional.empty();
        }
        String candidate = uid.strip();
        return isValidEmail(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static boolean isValidEmail(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c <= ' ' || c == 127) {
                return false;
            }
        }
        int at = candidate.indexOf('@');
        return at > 0 && at == candidate.lastIndexOf('@') && at < candidate.length() - 1;
    }

    public record VerifiedKeySubmission(String armoredKey, List<VerifiedKeyIdentity> verifiedIdentities) {}

    record VerifiedKeyMaterial(String armoredKey, PGPPublicKeyRingCollection keyRingCollection) {}

    public record VerifiedKeyIdentity(String fingerprint, String uidRaw, String uidEmail) {}
}
