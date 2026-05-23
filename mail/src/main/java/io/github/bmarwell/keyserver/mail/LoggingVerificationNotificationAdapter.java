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
package io.github.bmarwell.keyserver.mail;

import io.github.bmarwell.keyserver.application.port.notification.VerificationNotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import java.net.URI;
import java.util.logging.Logger;

/// First-iteration adapter for {@link VerificationNotificationPort}.
///
/// Instead of sending an email, this adapter logs the verification URI at
/// `INFO` level.  This lets operators confirm the key submission flow end-to-end
/// without configuring an SMTP server.
///
/// Replace with (or add alongside) `SmtpVerificationNotificationAdapter` when
/// real email delivery is needed.  The port interface is unchanged.
@Default
@ApplicationScoped
public class LoggingVerificationNotificationAdapter implements VerificationNotificationPort {

    private static final Logger LOG = Logger.getLogger(LoggingVerificationNotificationAdapter.class.getName());

    @Override
    public void notifyPendingVerification(String toEmail, String fingerprint, URI verificationUri) {
        LOG.info(() ->
                ("Verification pending for <%s> key %s — visit: %s").formatted(toEmail, fingerprint, verificationUri));
    }
}
