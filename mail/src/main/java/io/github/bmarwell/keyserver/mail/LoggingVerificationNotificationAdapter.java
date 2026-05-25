/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.mail;

import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.net.URI;
import java.util.logging.Logger;

/// First-iteration adapter for {@link VerificationNotificationPort}.
///
/// Instead of sending an email, this adapter logs the verification URI at
/// `INFO` level.  This lets operators confirm the key submission flow end-to-end
/// without configuring an SMTP server.
///
/// This bean is an `@Alternative` with an application-range priority (2000) so
/// it is globally active while no higher-priority implementation (e.g., an SMTP
/// adapter) is present.  Adding an SMTP adapter with a higher `@Priority` value
/// will automatically supersede this adapter without any `beans.xml` changes.
///
/// Replace with (or add alongside) `SmtpVerificationNotificationAdapter` when
/// real email delivery is needed.  The port interface is unchanged.
@Alternative
@Priority(2000)
@ApplicationScoped
public class LoggingVerificationNotificationAdapter implements VerificationNotificationPort {

    private static final Logger LOG = Logger.getLogger(LoggingVerificationNotificationAdapter.class.getName());

    @Override
    public void notifyPendingVerification(String toEmail, String fingerprint, URI verificationUri) {
        // NOTE: The full verification URI (which acts as a bearer token) is logged here.
        // This is intentional for the first iteration where no SMTP transport is configured
        // and operators need to retrieve the link from logs to complete testing.
        // TODO: Once SMTP is wired, either remove this adapter or downgrade to FINE / DEBUG
        //       so that the token is not visible in production logs.  See implementation-plan §8.
        LOG.info(() ->
                ("Verification pending for <%s> key %s — visit: %s").formatted(toEmail, fingerprint, verificationUri));
    }
}
