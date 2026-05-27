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
import java.util.Map;
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
///
/// All derived values (epoch seconds, percent-encoded UIDs, flag strings) are computed
/// in Java before being passed to templates.  Templates handle output structure only.
@ApplicationScoped
public class HkpIndexRenderer extends AbstractFreemarkerRenderer {

    /// Renders the machine-readable HKP index format (`options=mr`).
    ///
    /// Format per the HKP draft:
    /// <pre>
    /// info:1:&lt;count&gt;
    /// pub:&lt;fingerprint&gt;:&lt;algo&gt;:&lt;keylen&gt;:&lt;ctime&gt;:&lt;exptime&gt;:&lt;flags&gt;
    /// uid:&lt;pct-encoded-uid&gt;:&lt;ctime&gt;:&lt;exptime&gt;:&lt;flags&gt;
    /// </pre>
    ///
    /// Flags (in spec order): `r` if revoked, `d` if disabled on this keyserver,
    /// `e` if expired (expiration is in the past), or empty.
    /// Key length is empty for ECC keys (bit strength is null), numeric for RSA/DSA.
    /// The `d` flag is a keyserver-administrative concept; it does not appear on
    /// `uid:` lines since UIDs are not disabled independently.
    public String renderMachineReadable(List<KeyIndexResult> results) {
        Instant now = Instant.now();
        List<Map<String, Object>> keys =
                results.stream().map(key -> buildMrKeyModel(key, now)).toList();
        Map<String, Object> model = Map.of("keyCount", results.size(), "keys", keys);
        return processTemplate("hkp-index-mr.ftl", model);
    }

    /// Renders a simple HTML table for browser display.
    ///
    /// The HTML is intentionally minimal — it is not required for GnuPG interoperability
    /// and is provided as a human-readable fallback only.
    public String renderHtml(List<KeyIndexResult> results) {
        Instant now = Instant.now();
        List<Map<String, Object>> keys =
                results.stream().map(key -> buildHtmlKeyModel(key, now)).toList();
        Map<String, Object> model = Map.of("keyCount", results.size(), "keys", keys);
        return processTemplate("hkp-index-html.ftlh", model);
    }

    private Map<String, Object> buildMrKeyModel(KeyIndexResult key, Instant now) {
        List<Map<String, Object>> uids = key.verifiedUids().stream()
                .map(uid -> buildMrUidModel(uid, now))
                .toList();
        return Map.of(
                "fingerprint", key.fingerprint(),
                "algorithm", key.algorithm(),
                "bitStrength", key.bitStrength().map(Object::toString).orElse(""),
                "ctimeEpoch", toEpochSeconds(key.creationTime()),
                "exptimeEpoch",
                        key.expirationTime()
                                .map(HkpIndexRenderer::toEpochSeconds)
                                .orElse(""),
                "flags", computeFlags(key.revoked(), key.disabled(), key.expirationTime(), now),
                "uids", uids);
    }

    private Map<String, Object> buildMrUidModel(UidIndexEntry uid, Instant now) {
        // Use %20 for spaces (RFC 3986 percent-encoding) rather than +.
        // URLEncoder uses application/x-www-form-urlencoded which encodes spaces as '+',
        // but '+' is a legal literal character in UID strings and would be mis-decoded
        // by strict HKP clients expecting RFC 3986.
        String encodedUid =
                URLEncoder.encode(uid.uidRaw(), StandardCharsets.UTF_8).replace("+", "%20");
        return Map.of(
                "encodedUid", encodedUid,
                "ctimeEpoch",
                        uid.creationTime().map(HkpIndexRenderer::toEpochSeconds).orElse(""),
                "exptimeEpoch",
                        uid.expirationTime()
                                .map(HkpIndexRenderer::toEpochSeconds)
                                .orElse(""),
                // UIDs are not disabled independently; pass false for the 'd' flag.
                "flags", computeFlags(uid.revoked(), false, uid.expirationTime(), now));
    }

    private Map<String, Object> buildHtmlKeyModel(KeyIndexResult key, Instant now) {
        List<Map<String, Object>> uids = key.verifiedUids().stream()
                .map(uid -> Map.<String, Object>of("uidRaw", uid.uidRaw()))
                .toList();
        return Map.of(
                "fingerprint", key.fingerprint(),
                "algorithm", key.algorithm(),
                "creationTime", key.creationTime().toString(),
                "expirationTime", key.expirationTime().map(Object::toString).orElse(""),
                "flags", computeFlags(key.revoked(), key.disabled(), key.expirationTime(), now),
                "uids", uids);
    }

    private static String toEpochSeconds(OffsetDateTime dt) {
        return String.valueOf(dt.toEpochSecond());
    }

    private static String computeFlags(
            boolean revoked, boolean disabled, Optional<OffsetDateTime> expiration, Instant now) {
        StringBuilder flags = new StringBuilder();
        if (revoked) {
            flags.append('r');
        }
        if (disabled) {
            flags.append('d');
        }
        if (expiration.isPresent() && expiration.get().toInstant().isBefore(now)) {
            flags.append('e');
        }
        return flags.toString();
    }
}
