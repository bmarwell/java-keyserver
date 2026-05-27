/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
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
    public void recordFingerprint(long btxId, String fingerprint) {
        BusinessTransactionEntity entity = getEntityManager().find(BusinessTransactionEntity.class, btxId);
        if (entity != null) {
            entity.markFingerprintSet(fingerprint);
            LOG.log(Level.FINE, "BTX {0} fingerprint set [{1}]", new Object[] {btxId, fingerprint});
        } else {
            LOG.log(Level.WARNING, "BTX {0} not found when recording fingerprint [{1}]", new Object[] {
                btxId, fingerprint
            });
        }
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
