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

import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.repository.entity.BusinessTransactionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/// JPA implementation of `BusinessTransactionRepository`.
///
/// Every method uses `REQUIRES_NEW` so its write commits independently of whatever
/// JTA transaction the caller holds.  This guarantees that BTX status rows survive
/// a command-level rollback and are always visible in the admin audit view.
@ApplicationScoped
public class PersistentBusinessTransactionRepository extends BaseRepository implements BusinessTransactionRepository {

    private static final Logger LOG = Logger.getLogger(PersistentBusinessTransactionRepository.class.getName());

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordStarted(long btxId, String commandType, @Nullable String callerIp) {
        var entity = BusinessTransactionEntity.started(btxId, commandType, callerIp);
        getEntityManager().persist(entity);
        LOG.log(Level.FINE, "BTX {0} STARTED [{1}]", new Object[] {btxId, commandType});
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordCompleted(long btxId) {
        BusinessTransactionEntity entity = getEntityManager().find(BusinessTransactionEntity.class, btxId);
        if (entity != null) {
            entity.markCompleted();
        }
        LOG.log(Level.FINE, "BTX {0} COMPLETED", btxId);
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailed(long btxId, String errorType, @Nullable String errorMessage) {
        BusinessTransactionEntity entity = getEntityManager().find(BusinessTransactionEntity.class, btxId);
        if (entity != null) {
            entity.markFailed(errorType, errorMessage);
        }
        LOG.log(Level.FINE, "BTX {0} FAILED [{1}]", new Object[] {btxId, errorType});
    }
}
