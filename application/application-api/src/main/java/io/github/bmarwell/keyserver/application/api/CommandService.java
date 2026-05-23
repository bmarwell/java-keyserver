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
package io.github.bmarwell.keyserver.application.api;

import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import java.util.concurrent.CompletableFuture;

public interface CommandService {

    /// Executes the given command asynchronously in a dedicated virtual-thread executor.
    ///
    /// Returns a `CompletableFuture` that completes with the handler's result on success,
    /// or completes exceptionally with a `KeyServerException` subtype on business errors.
    <T extends KeyServerCommand> CompletableFuture<KeyServerCommandResponse> handleCommand(T keyServerCommand);
}
