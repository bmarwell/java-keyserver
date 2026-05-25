/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;

public abstract class AbstractKeyServerCommandHandler<T extends KeyServerCommand> implements CommandHandler<T> {

    public KeyServerCommandResponse execute(KeyServerCommand command, CommandCallerContext callerContext) {
        return doExecute((T) command, callerContext);
    }

    abstract KeyServerCommandResponse doExecute(T command, CommandCallerContext callerContext);
}
