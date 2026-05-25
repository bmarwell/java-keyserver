/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.commands;

/// Command issued when an owner clicks a verification link to confirm control
/// of an email address associated with a submitted PGP key.
///
/// The {@code token} is the TSID of the verification-queue entry, encoded as
/// an unsigned decimal string (the same format embedded in the `/verify/{token}`
/// URI).  The handler is responsible for parsing it back to a `long`.
///
/// Caller metadata travels in the accompanying {@link CommandCallerContext}
/// as with all other commands.
public record VerifyUidCommand(String token) implements KeyServerCommand {}
