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

/// Command to submit a PGP public key for email-based ownership verification.
///
/// The primary adapter (`AddEndpoint`) reads the ASCII-armored key text from the
/// HTTP request body **synchronously**, anonymizes the caller's IP, and then
/// builds this command before handing it to `CommandService`.  Passing a
/// `String` (rather than an `InputStream`) avoids a closed-stream race when the
/// virtual-thread executor picks up the command after the HTTP thread returns.
///
/// The `anonymizedClientIp` field must already be anonymized (IPv4 last octet
/// zeroed, IPv6 last 80 bits zeroed) before this record is constructed.  No
/// downstream component should receive or store a raw IP.
public record AddKeyToVerificationQueueCommand(String keyText, String anonymizedClientIp) implements KeyServerCommand {}
