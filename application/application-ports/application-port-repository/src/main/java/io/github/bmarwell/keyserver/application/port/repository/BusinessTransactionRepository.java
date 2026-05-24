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

    /// Updates the row to state `COMPLETED` and sets `completed_at`.
    void recordCompleted(long btxId);

    /// Updates the row to state `FAILED`, sets `completed_at`, and stores the
    /// exception class name and message for admin audit purposes.
    ///
    /// @param errorType    simple class name of the thrown exception
    /// @param errorMessage `Exception.getMessage()`, may be `null`
    void recordFailed(long btxId, String errorType, @Nullable String errorMessage);
}
