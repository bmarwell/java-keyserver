/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.KeyIndexResult;
import io.github.bmarwell.keyserver.application.api.UidIndexEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/// Renders HKP `op=index` responses in machine-readable and HTML formats.
///
/// Machine-readable format (`options=mr`) follows the HKP draft specification:
/// each key block starts with a `pub:` line containing fingerprint, algorithm, key length,
/// creation timestamp, expiration timestamp, and flags.  Each verified UID follows as a
/// `uid:` line.  The response begins with an `info:` header line.
///
/// The HTML format is a simple table — sufficient for browser inspection but not required
/// for GnuPG interoperability.
@ApplicationScoped
public class HkpIndexRenderer {

    /// Renders the machine-readable HKP index format (`options=mr`).
    ///
    /// Format per the HKP draft:
    /// <pre>
    /// info:1:&lt;count&gt;
    /// pub:&lt;fingerprint&gt;:&lt;algo&gt;:&lt;keylen&gt;:&lt;ctime&gt;:&lt;exptime&gt;:&lt;flags&gt;
    /// uid:&lt;pct-encoded-uid&gt;:&lt;ctime&gt;:&lt;exptime&gt;:&lt;flags&gt;
    /// </pre>
    ///
    /// Flags: `r` if revoked, `e` if expired (expiration is in the past), or empty.
    /// Key length is empty for ECC keys (bit strength is null), numeric for RSA/DSA.
    public String renderMachineReadable(List<KeyIndexResult> results) {
        Instant now = Instant.now();
        StringBuilder sb = new StringBuilder();
        sb.append("info:1:").append(results.size()).append('\n');
        for (KeyIndexResult key : results) {
            sb.append("pub:");
            sb.append(key.fingerprint()).append(':');
            sb.append(key.algorithm()).append(':');
            key.bitStrength().ifPresent(bs -> sb.append(bs));
            sb.append(':');
            sb.append(toEpochSeconds(key.creationTime())).append(':');
            sb.append(key.expirationTime().map(HkpIndexRenderer::toEpochSeconds).orElse(""))
                    .append(':');
            sb.append(computeFlags(key.revoked(), key.expirationTime(), now));
            sb.append('\n');
            for (UidIndexEntry uid : key.verifiedUids()) {
                sb.append("uid:");
                sb.append(URLEncoder.encode(uid.uidRaw(), StandardCharsets.UTF_8))
                        .append(':');
                sb.append(uid.creationTime()
                                .map(HkpIndexRenderer::toEpochSeconds)
                                .orElse(""))
                        .append(':');
                sb.append(uid.expirationTime()
                                .map(HkpIndexRenderer::toEpochSeconds)
                                .orElse(""))
                        .append(':');
                sb.append(computeFlags(uid.revoked(), uid.expirationTime(), now));
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /// Renders a simple HTML table for browser display.
    ///
    /// The HTML is intentionally minimal — it is not required for GnuPG interoperability
    /// and is provided as a human-readable fallback only.
    public String renderHtml(List<KeyIndexResult> results) {
        Instant now = Instant.now();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><h1>Search results: ").append(results.size()).append(" key(s)</h1>\n");
        sb.append("<table border=\"1\"><tr><th>Fingerprint</th><th>Algorithm</th>"
                + "<th>Created</th><th>Expires</th><th>Flags</th><th>UIDs</th></tr>\n");
        for (KeyIndexResult key : results) {
            sb.append("<tr>");
            sb.append("<td>").append(htmlEscape(key.fingerprint())).append("</td>");
            sb.append("<td>").append(key.algorithm()).append("</td>");
            sb.append("<td>").append(key.creationTime()).append("</td>");
            sb.append("<td>")
                    .append(key.expirationTime().map(Object::toString).orElse(""))
                    .append("</td>");
            sb.append("<td>")
                    .append(computeFlags(key.revoked(), key.expirationTime(), now))
                    .append("</td>");
            sb.append("<td>");
            for (UidIndexEntry uid : key.verifiedUids()) {
                sb.append(htmlEscape(uid.uidRaw())).append("<br>");
            }
            sb.append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    private static String toEpochSeconds(OffsetDateTime dt) {
        return String.valueOf(dt.toEpochSecond());
    }

    private static String computeFlags(boolean revoked, Optional<OffsetDateTime> expiration, Instant now) {
        StringBuilder flags = new StringBuilder();
        if (revoked) {
            flags.append('r');
        }
        if (expiration.isPresent() && expiration.get().toInstant().isBefore(now)) {
            flags.append('e');
        }
        return flags.toString();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
