/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;

public interface CommandService {

    /// Submits the command to a virtual-thread executor and returns immediately.
    ///
    /// The command runs asynchronously inside a business transaction (BTX).
    /// Any failure is recorded in the BTX audit row and logged; it does NOT
    /// propagate back to the caller.
    ///
    /// @param keyServerCommand the command to execute
    /// @param callerContext    caller metadata (pre-anonymized IP, etc.); use
    ///                         {@link CommandCallerContext#empty()} if unavailable
    <T extends KeyServerCommand> void handleCommand(T keyServerCommand, CommandCallerContext callerContext);
}
