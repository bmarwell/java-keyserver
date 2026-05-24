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
