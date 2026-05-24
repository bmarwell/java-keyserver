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
package io.github.bmarwell.keyserver.application.api.ex;

import java.util.Optional;

/// Thrown when a submitted key carries more email-bearing UIDs than the server
/// is willing to process.
///
/// ## Rationale
///
/// The 2019 SKS keyserver poisoning attack demonstrated that accepting an
/// unbounded number of UIDs per key enables a trivial denial-of-service: an
/// attacker uploads a key with tens of thousands of UIDs, overwhelming anything
/// that tries to process or display it (GnuPG, Thunderbird, Kleopatra, …).
/// keys.openpgp.org responded by capping at **5** verified email UIDs.
///
/// This implementation uses a cap of
/// {@link
/// io.github.bmarwell.keyserver.application.core.cmdhandler.AddKeyToVerificationQueueCommandHandler#MAX_EMAIL_UIDS}
/// (currently 20).  The reasoning:
///
/// - Legitimate real-world keys carry 1–3 UIDs (personal, work, alias).
/// - Power users with many email aliases rarely exceed 10.
/// - 20 leaves ample headroom while being 50 000× below DoS territory.
/// - Email-based verification already limits throughput naturally (each UID
///   requires one outbound email and one user interaction), so the cap mainly
///   defends against bulk pre-verification resource exhaustion.
public final class TooManyVerifiableUidsException extends KeyValidationException {

    public TooManyVerifiableUidsException(String message) {
        super(message, Optional.empty());
    }
}
