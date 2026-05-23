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
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.CommandService;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.core.cmdhandler.CommandHandler;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.hypersistence.tsid.TSID;
import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.concurrent.ManagedExecutorDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.io.Serializable;
import java.util.logging.Level;
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
/// 4. The command handler runs inside a `@Transactional` boundary.
/// 5. On success the BTX row is updated to `COMPLETED` (also `REQUIRES_NEW`).
/// 6. On any exception the BTX row is updated to `FAILED` (`REQUIRES_NEW`) and
///    the exception is logged.  It does NOT propagate back — the caller fired and forgot.
@ManagedExecutorDefinition(name = "java:app/concurrent/KeyServerCommandExecutor", virtual = true, maxAsync = -1)
@Default
@ApplicationScoped
public class KeyServerCommandService implements CommandService, Serializable {

    private static final Logger LOG = Logger.getLogger(KeyServerCommandService.class.getName());

    @Inject
    @Any
    Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers;

    @Inject
    BusinessTransactionRepository btxRepository;

    @Inject
    BusinessTransactionContext btxContext;

    @Inject
    TSID.Factory tsidFactory;

    @Asynchronous(executor = "java:app/concurrent/KeyServerCommandExecutor")
    @Override
    public <T extends KeyServerCommand> void handleCommand(T keyServerCommand) {
        long btxId = tsidFactory.generate().toLong();
        String commandType = keyServerCommand.getClass().getSimpleName();

        btxRepository.recordStarted(btxId, commandType, null);
        btxContext.initialize(btxId);

        try {
            dispatch(keyServerCommand);
            btxRepository.recordCompleted(btxId);
        } catch (Exception ex) {
            btxRepository.recordFailed(btxId, ex.getClass().getSimpleName(), ex.getMessage());
            LOG.log(Level.WARNING, "Command {0} failed for BTX {1}: {2}", new Object[] {
                commandType, btxId, ex.getMessage()
            });
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    private <T extends KeyServerCommand> void dispatch(T command) {
        CommandHandler<T> handler = (CommandHandler<T>) commandHandlers.stream()
                .filter(ch -> ch.canHandle(command))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No CommandHandler for: " + command.getClass().getName()));

        handler.execute(command);
    }

    // CDI-friendly setters for testing

    public void setCommandHandlers(Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers) {
        this.commandHandlers = commandHandlers;
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
