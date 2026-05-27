/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;

/// Primary (inbound) port for synchronous UID verification.
///
/// Implementations handle the full token-to-publication flow synchronously,
/// allowing callers to surface success or failure to the user immediately.
/// This is the preferred path for the HKP {@code /pks/verify/{token}} endpoint and
/// for any future VKS REST endpoint that needs to confirm verification outcome inline.
///
/// The async path via {@link CommandService} + {@code VerifyUidCommand} remains
/// available but does not surface outcome to the caller.
public interface VerificationService {

    /// Verifies a UID via its token synchronously.
    ///
    /// Parses the token, loads and validates the queue entry, marks it verified,
    /// and publishes the corresponding UID to the key store — all within a single
    /// transaction whose outcome the caller can observe immediately.
    ///
    /// @param token the unsigned-decimal string TSID from the verification URI
    /// @return a {@link VerificationResult} describing what was verified
    /// @throws TokenInvalidException if the token cannot be parsed, is not found,
    ///         or has already been consumed
    /// @throws TokenExpiredException if the token is found but its deadline has passed
    VerificationResult verifyUid(String token);
}
