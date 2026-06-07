/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.it.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;

/**
 * Generates throw-away OpenPGP public key material for integration tests.
 *
 * <p>Uses BouncyCastle's pure {@code Bc*} API so no JCE provider registration
 * ({@code Security.addProvider}) is required.  Keys are generated fresh for each
 * test run and must not be committed to the repository.
 */
public final class TestPgpKeyGenerator {

    private TestPgpKeyGenerator() {}

    /**
     * Generates a fresh RSA-2048 OpenPGP public key ring and returns it as an
     * ASCII-armored string suitable for {@code POST /pks/add?keytext=…}.
     *
     * @param userId OpenPGP user-ID string, e.g. {@code "Test User <test@example.com>"}
     * @return ASCII-armored public key block
     * @throws PGPException if key-ring construction fails
     * @throws IOException  if armoring fails
     */
    public static String generateArmoredPublicKey(String userId) throws PGPException, IOException {
        // Generate RSA 2048 key pair using BC's own generators — no JCE needed.
        RSAKeyPairGenerator rsaGen = new RSAKeyPairGenerator();
        rsaGen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), 2048, 12));

        BcPGPDigestCalculatorProvider digestCalcProvider = new BcPGPDigestCalculatorProvider();

        BcPGPKeyPair pgpKeyPair = new BcPGPKeyPair(PGPPublicKey.RSA_GENERAL, rsaGen.generateKeyPair(), new Date());

        /*
         * PGPKeyRingGenerator produces a key ring with a self-signature over the
         * user-ID, which is required for the key to be accepted by the server.
         * An empty passphrase (char[0]) means the secret key is unencrypted — fine
         * for ephemeral test keys that are never persisted.
         *
         * The 4th argument (checksumCalculator) MUST be SHA-1 per RFC 4880 §5.5.3:
         * the OpenPGP wire format mandates SHA-1 for secret-key material checksums
         * and BouncyCastle enforces this at runtime. The certification self-signature
         * (BcPGPContentSignerBuilder) uses SHA-256.
         */
        PGPKeyRingGenerator ringGen = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                pgpKeyPair,
                userId,
                digestCalcProvider.get(HashAlgorithmTags.SHA1),
                null,
                null,
                new BcPGPContentSignerBuilder(PGPPublicKey.RSA_GENERAL, HashAlgorithmTags.SHA256),
                new BcPBESecretKeyEncryptorBuilder(
                                SymmetricKeyAlgorithmTags.AES_256, digestCalcProvider.get(HashAlgorithmTags.SHA256))
                        .build(new char[0]));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ArmoredOutputStream aos = new ArmoredOutputStream(baos)) {
            ringGen.generatePublicKeyRing().encode(aos);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
