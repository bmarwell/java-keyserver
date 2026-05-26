/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.cmdhandler.verification;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;

/// Default verification registry used when a command handler has no explicit
/// pre-execution verification steps yet.
public final class NoOpCommandVerificationRegistry<T extends KeyServerCommand>
        implements CommandVerificationRegistry<T, NoCommandVerification> {

    @Override
    public NoCommandVerification verify(T command, CommandCallerContext callerContext) {
        return NoCommandVerification.INSTANCE;
    }
}
