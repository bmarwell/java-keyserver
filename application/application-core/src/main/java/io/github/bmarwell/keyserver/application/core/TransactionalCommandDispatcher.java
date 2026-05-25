/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.core.cmdhandler.CommandHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/// Locates the right {@link CommandHandler} for a command and executes it inside a
/// single JTA transaction.
///
/// ## Why a separate bean?
///
/// CDI interceptors (e.g., `@Transactional`) are applied via proxy and only fire on
/// method calls that pass **through the proxy** — i.e., calls from one CDI bean to
/// another.  A private or self-invoked method in the same class bypasses the proxy
/// and therefore receives no interception.
///
/// By extracting the dispatch step into its own `@ApplicationScoped` bean, the
/// `@Transactional` annotation reliably opens a JTA transaction around the handler
/// call so that all DAO writes made by the handler participate in a single unit of
/// work.  BTX lifecycle writes (`recordStarted`, `recordCompleted`, `recordFailed`)
/// use `REQUIRES_NEW` and therefore always commit independently.
@ApplicationScoped
class TransactionalCommandDispatcher {

    @Inject
    @Any
    Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers;

    /// Finds the handler for {@code command} and executes it within a `REQUIRED`
    /// JTA transaction.  Throws {@link UnsupportedOperationException} (which the
    /// caller catches) if no handler is registered.
    @Transactional
    @SuppressWarnings("unchecked")
    public <T extends KeyServerCommand> void dispatch(T command, CommandCallerContext callerContext) {
        CommandHandler<T> handler = (CommandHandler<T>) commandHandlers.stream()
                .filter(ch -> ch.canHandle(command))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No CommandHandler for: " + command.getClass().getName()));

        handler.execute(command, callerContext);
    }

    // CDI-friendly setter for unit testing
    public void setCommandHandlers(Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }
}
