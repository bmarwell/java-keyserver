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

import io.github.bmarwell.keyserver.application.api.KeyQueueRepositoryService;
import io.github.bmarwell.keyserver.application.api.commands.AddKeyToVerificationQueueCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.port.mail.MailService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Iterator;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.util.encoders.Hex;

@RequestScoped
public class AddKeyToVerificationQueueCommandHandler
        extends AbstractKeyServerCommandHandler<AddKeyToVerificationQueueCommand> {

    @Inject
    KeyQueueRepositoryService keyQueueRepositoryService;

    @Inject
    MailService mailService;

    @Override
    public <C extends KeyServerCommand> boolean canHandle(C command) {
        return command instanceof AddKeyToVerificationQueueCommand;
    }

    @Override
    KeyServerCommandResponse doExecute(AddKeyToVerificationQueueCommand command) {
        try (var decoderStream = PGPUtil.getDecoderStream(command.asciiArmoredKeyRing())) {
            PGPPublicKeyRingCollection pgpPub =
                    new PGPPublicKeyRingCollection(decoderStream, new BcKeyFingerprintCalculator());

            for (Iterator<PGPPublicKeyRing> rings = pgpPub.getKeyRings(); rings.hasNext(); ) {
                PGPPublicKeyRing keyring = rings.next();

                for (Iterator<PGPPublicKey> keys = keyring.getPublicKeys(); keys.hasNext(); ) {
                    PGPPublicKey key = keys.next();

                    System.out.println("next key");
                    System.out.println("is master key: " + key.isMasterKey());
                    System.out.println("can encrypt: " + key.isEncryptionKey());
                    System.out.println("algo: " + key.getAlgorithm());
                    System.out.println("bits: " + key.getBitStrength());

                    for (Iterator<String> userIds = key.getUserIDs(); userIds.hasNext(); ) {
                        String userId = userIds.next();
                        System.out.println(userId);
                    }

                    System.out.println(Long.toHexString(key.getKeyID()));
                    System.out.println(Hex.toHexString(key.getFingerprint()));
                }
            }
        } catch (IOException | PGPException e) {
            throw new RuntimeException(e);
        }

        this.keyQueueRepositoryService.addKeyToRepository(command.repositoryName(), null);
        // TODO: when successful, send verification mail
        // TODO: need a verification of all UIDs (mail addresses, if exist)
        // TODO: drop UIDs without mail address
        // TODO: drop revoked
        // TODO: drop expired
        // TODO: drop "created in future"

        throw new UnsupportedOperationException("not implemented");
    }

    public KeyQueueRepositoryService getKeyQueueRepositoryService() {
        return keyQueueRepositoryService;
    }

    public void setKeyQueueRepositoryService(KeyQueueRepositoryService keyQueueRepositoryService) {
        this.keyQueueRepositoryService = keyQueueRepositoryService;
    }

    public MailService getMailService() {
        return mailService;
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }
}
