/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;

/// Central verification entry point for one command type.
public interface CommandVerificationRegistry<T extends KeyServerCommand, V> {

    V verify(T command, CommandCallerContext callerContext);
}
