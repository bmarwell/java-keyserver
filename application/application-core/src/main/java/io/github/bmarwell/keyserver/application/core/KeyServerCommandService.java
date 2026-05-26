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
/// 4. The command handler runs inside a `@Transactional` boundary provided by
///    the injected {@link TransactionalCommandDispatcher}.  Using a separate
///    bean ensures CDI interceptors fire through the proxy rather than being
///    bypassed by a same-bean self-invocation.
/// 5. On success the BTX row is updated to `COMPLETED` (also `REQUIRES_NEW`).
/// 6. If the completion write itself fails after a successful handler, the service
///    falls back to marking the BTX row `FAILED` with a dedicated error type so the
///    row still reaches a terminal state.
/// 7. On any exception the BTX row is updated to `FAILED` (`REQUIRES_NEW`) and
///    the exception is logged.  It does NOT propagate back — the caller fired and forgot.
@ManagedExecutorDefinition(name = "java:app/concurrent/KeyServerCommandExecutor", virtual = true, maxAsync = -1)
@Default
@ApplicationScoped
public class KeyServerCommandService implements CommandService, Serializable {

    static final String BTX_COMPLETION_WRITE_FAILURE = "BtxCompletionWriteFailure";

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

        this.btxRepository.recordStarted(btxId, commandType, callerContext.anonymizedCallerIp());
        this.btxContext.initialize(btxId);

        try {
            this.dispatcher.dispatch(keyServerCommand, callerContext);
        } catch (Exception ex) {
            this.btxRepository.recordFailed(btxId, ex.getClass().getSimpleName(), ex.getMessage());
            LOG.log(Level.WARNING, "Command {0} failed for BTX {1}: {2}", new Object[] {
                commandType, btxId, ex.getMessage()
            });
            return;
        }

        try {
            this.btxRepository.recordCompleted(btxId);
        } catch (Exception ex) {
            this.logWithThrowable(
                    Level.WARNING,
                    ex,
                    "Failed to mark BTX {0} COMPLETED after successful command {1}; attempting terminal FAILED fallback",
                    btxId,
                    commandType);
            try {
                this.btxRepository.recordFailed(btxId, BTX_COMPLETION_WRITE_FAILURE, ex.getMessage());
            } catch (Exception fallbackEx) {
                this.logWithThrowable(
                        Level.WARNING,
                        fallbackEx,
                        "Failed to mark BTX {0} FAILED after completion-write failure for command {1}",
                        btxId,
                        commandType);
            }
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
