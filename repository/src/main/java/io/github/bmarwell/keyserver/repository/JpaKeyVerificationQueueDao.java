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
package io.github.bmarwell.keyserver.repository;

import io.github.bmarwell.keyserver.application.port.repository.KeyVerificationQueueDao;
import io.github.bmarwell.keyserver.common.ids.PgpPublicKey;
import io.github.bmarwell.keyserver.common.ids.RepositoryName;
import io.github.bmarwell.keyserver.repository.pdo.PublicKeyQueuePdo;
import io.github.bmarwell.keyserver.repository.pdo.ReversedKeyFingerprint;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import java.util.random.RandomGenerator;

@Default
@ApplicationScoped
public class JpaKeyVerificationQueueDao extends BaseRepository implements KeyVerificationQueueDao {

    RandomGenerator random = RandomGenerator.getDefault();

    @Override
    public PgpPublicKey addKeyToQueue(RepositoryName repositoryName, PgpPublicKey publicKey, String secret) {
        final var em = getEntityManager();

        final var rfp = ReversedKeyFingerprint.fromFingerprint(publicKey.keyFingerprint());

        final var publicKeyQueuePdo = em.find(PublicKeyQueuePdo.class, rfp);

        if (publicKeyQueuePdo != null) {
            em.remove(publicKeyQueuePdo);
            em.detach(publicKeyQueuePdo);
        }

        final var newQueueKey = new PublicKeyQueuePdo(rfp, secret);

        em.merge(newQueueKey);

        return publicKey;
    }
}
