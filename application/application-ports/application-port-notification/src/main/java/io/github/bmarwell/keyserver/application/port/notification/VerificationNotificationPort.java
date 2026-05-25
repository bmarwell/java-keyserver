/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.port.notification;

import java.net.URI;

/// Secondary (outbound) port for verification notifications.
///
/// The application core calls this port after placing a key in the verification
/// queue.  The adapter chosen at runtime determines the delivery channel:
///
/// - **`LoggingVerificationNotificationAdapter`** (first iteration) — logs the
///   verification URL to the server log so operators can confirm the flow without
///   a real SMTP server.
/// - **`SmtpVerificationNotificationAdapter`** (future) — sends an actual email
///   to the key owner.
///
/// The port intentionally carries only the data the recipient needs; it does not
/// expose queue internals or token storage details.
public interface VerificationNotificationPort {

    /// Notify a key owner that one of their UIDs is pending email verification.
    ///
    /// @param toEmail         the email address extracted from the PGP UID
    /// @param fingerprint     hex fingerprint of the submitted key
    /// @param verificationUri the URI the user must visit to confirm ownership
    void notifyPendingVerification(String toEmail, String fingerprint, URI verificationUri);
}
