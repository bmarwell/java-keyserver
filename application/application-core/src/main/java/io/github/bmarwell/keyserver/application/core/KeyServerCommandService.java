/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.CommandService;
import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.hypersistence.tsid.TSID;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/// Dispatches commands to their handlers inside a dedicated virtual-thread executor.
///
/// ## Executor
///
/// The `@ManagedExecutorDefinition` annotation registers a named Jakarta Concurrency
/// executor backed by virtual threads (`virtual = true`).  `maxAsync = -1` removes
/// any artificial concurrency cap — virtual threads are cheap enough that the JVM
/// scheduler can be trusted to manage them.
///
/// ## Business transaction lifecycle
///
/// For every command:
///
/// 1. A TSID is generated via the shared node-aware `TSID.Factory`.
/// 2. A `business_transactions` row is written (`STARTED`) in a `REQUIRES_NEW`
///    JTA transaction — always durable, regardless of what follows.
/// 3. The BTX ID is stored in the `@RequestScoped` `BusinessTransactionContext`
///    so that any downstream component (audit writer, DAO) can reference it
///    without explicit parameter passing.
///    If either step 2 or 3 fails, the error is logged and the command is not dispatched.
/// 4. The command handler runs inside a `@Transactional` boundary provided by
///    the injected {@link TransactionalCommandDispatcher}.  Using a separate
///    bean ensures CDI interceptors fire through the proxy rather than being
///    bypassed by a same-bean self-invocation.
/// 5. On success the BTX row is updated to `COMPLETED` (also `REQUIRES_NEW`).
/// 6. If the completion write fails (transient fault), it is retried once.  Only
///    if both attempts fail does the service fall back to marking the BTX `FAILED`.
/// 7. On any exception the BTX row is updated to `FAILED` (`REQUIRES_NEW`) and
///    the exception is logged.  It does NOT propagate back — the caller fired and forgot.
@ManagedExecutorDefinition(name = "java:app/concurrent/KeyServerCommandExecutor", virtual = true, maxAsync = -1)
@Default
@ApplicationScoped
public class KeyServerCommandService implements CommandService, Serializable {
    private static final Logger LOG = Logger.getLogger(KeyServerCommandService.class.getName());

    @Inject
    TransactionalCommandDispatcher dispatcher;

    @Inject
    BusinessTransactionRepository btxRepository;

    @Inject
    BusinessTransactionContext btxContext;

    @Inject
    TSID.Factory tsidFactory;

    @Asynchronous(executor = "java:app/concurrent/KeyServerCommandExecutor")
    @Override
    public <T extends KeyServerCommand> void handleCommand(T keyServerCommand, CommandCallerContext callerContext) {
        long btxId = this.tsidFactory.generate().toLong();
        String commandType = keyServerCommand.getClass().getSimpleName();
        boolean startedRecorded = false;
        boolean dispatchedSuccessfully = false;

        try {
            this.btxRepository.recordStarted(btxId, commandType, callerContext.anonymizedCallerIp());
            startedRecorded = true;
            this.btxContext.initialize(btxId);
            this.dispatcher.dispatch(keyServerCommand, callerContext);
            dispatchedSuccessfully = true;
        } catch (Exception ex) {
            if (!startedRecorded) {
                this.logWithThrowable(
                        Level.WARNING,
                        ex,
                        "Failed to record STARTED BTX {0} for command {1}; command not dispatched",
                        btxId,
                        commandType);
                return;
            }
            this.recordFailedSafely(
                    btxId,
                    commandType,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex,
                    "Failed to prepare or dispatch command {0} for BTX {1}",
                    commandType,
                    btxId);
        } finally {
            if (dispatchedSuccessfully) {
                this.recordCompletedWithRetry(btxId, commandType);
            }
        }
    }

    /// Attempts to mark the BTX `COMPLETED`, retrying once on failure.
    ///
    /// The most common cause of a completion-write failure is transient (connection
    /// hiccup, lock timeout).  Because each call uses `REQUIRES_NEW`, a retry is
    /// safe and will land the row in the correct `COMPLETED` state.  Only if both
    /// attempts fail does the method fall back to persisting a `FAILED` terminal state.
    private void recordCompletedWithRetry(long btxId, String commandType) {
        try {
            this.btxRepository.recordCompleted(btxId);
        } catch (Exception firstEx) {
            this.logWithThrowable(
                    Level.WARNING,
                    firstEx,
                    "BTX {0} completion write failed for command {1}; retrying once",
                    btxId,
                    commandType);
            try {
                this.btxRepository.recordCompleted(btxId);
            } catch (Exception retryEx) {
                this.recordFailedSafely(
                        btxId,
                        commandType,
                        retryEx.getClass().getSimpleName(),
                        retryEx.getMessage(),
                        retryEx,
                        "BTX {0} completion write failed after retry for command {1}; marking FAILED",
                        btxId,
                        commandType);
            }
        }
    }

    private void recordFailedSafely(
            long btxId,
            String commandType,
            String errorType,
            @Nullable String errorMessage,
            Throwable throwable,
            String logMessage,
            Object... logParameters) {
        this.logWithThrowable(Level.WARNING, throwable, logMessage, logParameters);
        try {
            this.btxRepository.recordFailed(btxId, errorType, errorMessage);
        } catch (Exception fallbackEx) {
            this.logWithThrowable(
                    Level.WARNING,
                    fallbackEx,
                    "Failed to persist BTX {0} FAILED state for command {1}",
                    btxId,
                    commandType);
        }
    }

    private void logWithThrowable(Level level, Throwable throwable, String message, Object... parameters) {
        LogRecord logRecord = new LogRecord(level, message);
        logRecord.setLoggerName(LOG.getName());
        logRecord.setParameters(parameters);
        logRecord.setThrown(throwable);
        LOG.log(logRecord);
    }

    // CDI-friendly setters for testing

    public void setDispatcher(TransactionalCommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void setBtxRepository(BusinessTransactionRepository btxRepository) {
        this.btxRepository = btxRepository;
    }

    public void setBtxContext(BusinessTransactionContext btxContext) {
        this.btxContext = btxContext;
    }

    public void setTsidFactory(TSID.Factory tsidFactory) {
        this.tsidFactory = tsidFactory;
    }
}
