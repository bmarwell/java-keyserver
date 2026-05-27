/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/// Renders HTML pages for the UID verification endpoint.
///
/// Produces three page types:
/// <ul>
///   <li>{@link #renderSuccess} — shown when a verification token was consumed successfully.</li>
///   <li>{@link #renderExpired} — shown when the token exists but its deadline has passed.</li>
///   <li>{@link #renderInvalid} — shown when the token is missing, garbled, or already consumed.</li>
/// </ul>
///
/// Templates are loaded from the classpath under {@code /templates/} using the Freemarker
/// {@link freemarker.template.Configuration} produced by {@link FreemarkerConfiguration}.
/// All three templates use the {@code .ftlh} extension, which activates Freemarker's HTML
/// auto-escaping so that user-supplied values (e.g. UID strings) cannot inject HTML.
///
/// The public method signatures are stable — switching the underlying template engine
/// (or reverting to StringBuilder rendering) does not affect callers.
@ApplicationScoped
public class VerifyRenderer extends AbstractFreemarkerRenderer {

    /// Renders a success page shown after a token is consumed and the UID published.
    ///
    /// @param result the outcome of the verification containing the UID raw string and fingerprint
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderSuccess(VerificationResult result) {
        return processTemplate(
                "verify-success.ftlh", Map.of("uidRaw", result.uidRaw(), "fingerprint", result.fingerprint()));
    }

    /// Renders an error page for expired verification tokens.
    ///
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderExpired() {
        return processTemplate("verify-expired.ftlh", Map.of());
    }

    /// Renders an error page for invalid, missing, or already-consumed verification tokens.
    ///
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderInvalid() {
        return processTemplate("verify-invalid.ftlh", Map.of());
    }
}
