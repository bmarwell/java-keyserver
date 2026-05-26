/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.core.cmdhandler.verification.CommandVerificationRegistry;

public abstract class AbstractKeyServerCommandHandler<T extends KeyServerCommand, V> implements CommandHandler<T> {

    @Override
    public final KeyServerCommandResponse execute(KeyServerCommand command, CommandCallerContext callerContext) {
        T typedCommand = (T) command;
        V verification = this.verificationRegistry().verify(typedCommand, callerContext);
        return this.doExecute(typedCommand, verification, callerContext);
    }

    protected abstract CommandVerificationRegistry<T, V> verificationRegistry();

    abstract KeyServerCommandResponse doExecute(T command, V verification, CommandCallerContext callerContext);
}
