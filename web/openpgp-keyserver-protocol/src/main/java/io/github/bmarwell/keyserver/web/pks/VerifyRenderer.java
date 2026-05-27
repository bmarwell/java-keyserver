/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import jakarta.enterprise.context.ApplicationScoped;

/// Renders HTML pages for the UID verification endpoint.
///
/// Produces three page types:
/// <ul>
///   <li>{@link #renderSuccess} — shown when a verification token was consumed successfully.</li>
///   <li>{@link #renderExpired} — shown when the token exists but its deadline has passed.</li>
///   <li>{@link #renderInvalid} — shown when the token is missing, garbled, or already consumed.</li>
/// </ul>
///
/// The rendering logic is intentionally isolated here so that the HTML generation
/// strategy can be changed — for example, migrated to a template engine such as
/// Freemarker or JMustache — without modifying the endpoint or the application service.
/// When migrating, only the body of each render method changes; their signatures and
/// the {@link VerifyEndpoint} caller remain stable.
@ApplicationScoped
public class VerifyRenderer {

    /// Renders a success page shown after a token is consumed and the UID published.
    ///
    /// @param result the outcome of the verification containing the UID raw string and fingerprint
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderSuccess(VerificationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html><head><meta charset=\"utf-8\"><title>Email address verified</title></head>\n");
        sb.append("<body>\n");
        sb.append("<h1>Email address verified</h1>\n");
        sb.append("<p>The following UID has been verified and is now published:</p>\n");
        sb.append("<p><strong>").append(htmlEscape(result.uidRaw())).append("</strong></p>\n");
        sb.append("<p>You can look up your key using the ");
        sb.append("<a href=\"/pks/lookup?op=index&amp;search=");
        sb.append(htmlEscape(result.fingerprint())).append("\">keyserver search</a>.</p>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    /// Renders an error page for expired verification tokens.
    ///
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderExpired() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html><head><meta charset=\"utf-8\"><title>Verification link expired</title></head>\n");
        sb.append("<body>\n");
        sb.append("<h1>Verification link expired</h1>\n");
        sb.append("<p>The verification link you followed has expired.</p>\n");
        sb.append("<p>Please upload your key again to receive a new verification email.</p>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    /// Renders an error page for invalid, missing, or already-consumed verification tokens.
    ///
    /// @return HTML string with DOCTYPE and charset declaration
    public String renderInvalid() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html><head><meta charset=\"utf-8\"><title>Invalid verification link</title></head>\n");
        sb.append("<body>\n");
        sb.append("<h1>Invalid verification link</h1>\n");
        sb.append("<p>The verification link you followed is no longer valid.</p>\n");
        sb.append("<p>It may have already been used, may not exist, or may be malformed.</p>\n");
        sb.append("<p>Please upload your key again to receive a new verification email.</p>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
