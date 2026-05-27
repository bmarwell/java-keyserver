/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.port.repository;

import org.jspecify.annotations.Nullable;

/// Outbound port for business-transaction lifecycle persistence.
///
/// All implementations must use `@Transactional(REQUIRES_NEW)` so that
/// BTX status rows are committed independently of the command's own JTA
/// transaction.  This ensures the STARTED row is always durable and the
/// COMPLETED / FAILED update survives a command-level rollback.
public interface BusinessTransactionRepository {

    /// Persists a new business-transaction row in state `STARTED`.
    ///
    /// @param btxId       TSID assigned by the command service
    /// @param commandType simple class name of the command
    /// @param callerIp    remote address of the HTTP client, or `null`
    void recordStarted(long btxId, String commandType, @Nullable String callerIp);

    /// Associates a key fingerprint with an existing BTX row.
    ///
    /// Called by command handlers once the fingerprint is known (after parsing or
    /// loading the key).  Uses `REQUIRES_NEW` so the update commits independently
    /// of the handler's own JTA transaction.
    ///
    /// @param btxId       TSID of the BTX row created by {@link #recordStarted}
    /// @param fingerprint hex fingerprint of the primary key
    void recordFingerprint(long btxId, String fingerprint);

    /// Updates the row to state `COMPLETED` and sets `completed_at`.
    void recordCompleted(long btxId);

    /// Updates the row to state `FAILED`, sets `completed_at`, and stores the
    /// exception class name and message for admin audit purposes.
    ///
    /// @param errorType    simple class name of the thrown exception
    /// @param errorMessage `Exception.getMessage()`, may be `null`
    void recordFailed(long btxId, String errorType, @Nullable String errorMessage);
}
