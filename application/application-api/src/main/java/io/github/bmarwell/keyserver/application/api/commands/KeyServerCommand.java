/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.commands;

/// Marker interface for all commands dispatched through the
/// {@link io.github.bmarwell.keyserver.application.api.CommandService}.
///
/// Commands carry business payload only.  Execution metadata (caller IP, etc.)
/// travels in the accompanying {@link CommandCallerContext}.
public interface KeyServerCommand {}
