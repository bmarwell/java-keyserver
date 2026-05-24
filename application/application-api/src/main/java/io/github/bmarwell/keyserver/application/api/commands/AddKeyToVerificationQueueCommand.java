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
