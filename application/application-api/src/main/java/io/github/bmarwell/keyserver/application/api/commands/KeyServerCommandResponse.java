/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.commands;

public interface KeyServerCommandResponse {
    record Success() implements KeyServerCommandResponse {}

    static KeyServerCommandResponse success() {
        return new Success();
    }
}
