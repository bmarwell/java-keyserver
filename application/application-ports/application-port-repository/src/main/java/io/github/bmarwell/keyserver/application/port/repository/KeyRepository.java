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
package io.github.bmarwell.keyserver.application.port.repository;

/// Secondary (outbound) port for the published key store.
///
/// Keys become visible through this repository only after at least one UID has
/// been email-verified.  Each verified UID is added individually; a single key
/// may be present with one UID and later enriched with additional UIDs as their
/// owners complete verification.
public interface KeyRepository {

    /// Publishes a newly verified UID, creating the key record if it does not yet
    /// exist or appending the UID to an existing key record.
    ///
    /// The `armoredKey` provided here is the **full** armored block as submitted by
    /// the uploader.  The adapter is responsible for stripping unverified UIDs
    /// before storing and serving the key (privacy-preserving, à la keys.openpgp.org).
    /// The stored `armored_key` column is updated on each call so the served block
    /// always reflects all UIDs verified so far.
    ///
    /// @param fingerprint hex fingerprint (40 chars for v4, 64 for v5)
    /// @param uidRaw      raw UID string as it appears in the key packet
    /// @param uidEmail    email address extracted from the UID
    /// @param armoredKey  full ASCII-armored public-key block
    void publishVerifiedUid(String fingerprint, String uidRaw, String uidEmail, String armoredKey);
}
