/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.api;

/// Outcome of a successful UID verification.
///
/// Returned by {@link VerificationService#verifyUid} when the token is valid,
/// not expired, and the corresponding UID has been published to the key store.
/// Callers may use these fields to build a user-facing confirmation page.
///
/// The fingerprint is in upper-case hex without spaces (40 chars for RSA/DSA/ECDSA
/// v4 fingerprints, 64 chars for v6 fingerprints).
public record VerificationResult(String uidRaw, String fingerprint) {}
