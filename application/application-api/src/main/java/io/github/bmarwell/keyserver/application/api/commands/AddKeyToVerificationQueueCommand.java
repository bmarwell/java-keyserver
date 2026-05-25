/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api.commands;

import org.jspecify.annotations.Nullable;

/// Command to submit a PGP public key for email-based ownership verification.
///
/// The primary adapter (`AddEndpoint`) reads the ASCII-armored key text from the
/// HTTP request body **synchronously** and builds this command before handing it
/// to `CommandService`.  Passing a `String` (rather than an `InputStream`) avoids
/// a closed-stream race when the virtual-thread executor picks up the command after
/// the HTTP thread returns.
///
/// `keyText` may be `null` when the form parameter is absent; the command handler
/// is responsible for validation and rejection in that case.
///
/// Caller metadata (anonymized IP) is **not** part of this record.  It travels in
/// the accompanying {@link CommandCallerContext} so that command records stay focused
/// on business payload.  See implementation-plan §8.7.
public record AddKeyToVerificationQueueCommand(@Nullable String keyText) implements KeyServerCommand {}
