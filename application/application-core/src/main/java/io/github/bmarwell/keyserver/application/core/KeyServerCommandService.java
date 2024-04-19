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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.Optional;

@Default
@ApplicationScoped
public class KeyServerCommandService implements CommandService, Serializable {

    @Inject
    @Any
    Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers;

    @Override
    public <T extends KeyServerCommand> void handleCommand(T keyServerCommand) {
        Optional<CommandHandler<? extends KeyServerCommand>> commandHandler = this.commandHandlers.stream()
                .filter(ch -> ch.canHandle(keyServerCommand))
                .findFirst();

        if (commandHandler.isEmpty()) {
            throw new UnsupportedOperationException("Not implemented: CommandHandler for command of type "
                    + keyServerCommand.getClass().getName());
        }

        commandHandler.orElseThrow().execute(keyServerCommand);
    }

    public Instance<CommandHandler<? extends KeyServerCommand>> getCommandHandlers() {
        return commandHandlers;
    }

    public void setCommandHandlers(Instance<CommandHandler<? extends KeyServerCommand>> commandHandlers) {
        this.commandHandlers = commandHandlers;
    }
}
