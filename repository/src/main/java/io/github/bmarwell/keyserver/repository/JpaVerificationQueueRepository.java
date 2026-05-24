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

import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository;
import io.github.bmarwell.keyserver.application.port.repository.VerificationQueueRepository.VerificationRequest;
import io.github.bmarwell.keyserver.repository.entity.VerificationQueueEntity;
import io.hypersistence.tsid.TSID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/// JPA adapter for the {@link VerificationQueueRepository} secondary port.
///
/// One row is inserted per UID per upload.  The TSID is generated here (in the
/// adapter) using the node-aware factory so the handler receives the token ID
/// immediately after calling {@link #enqueue} and can build the verification URI.
@Default
@ApplicationScoped
public class JpaVerificationQueueRepository extends BaseRepository implements VerificationQueueRepository {

    @Inject
    TSID.Factory tsidFactory;

    @Override
    @Transactional
    public long enqueue(VerificationRequest request) {
        long id = tsidFactory.generate().toLong();
        var entity = new VerificationQueueEntity(
                id,
                request.fingerprint(),
                request.uidRaw(),
                request.uidEmail(),
                request.armoredKey(),
                request.expiresAt());
        getEntityManager().persist(entity);
        return id;
    }

    public void setTsidFactory(TSID.Factory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }
}
