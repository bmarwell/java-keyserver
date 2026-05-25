/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids;

import java.net.InetAddress;
import java.net.UnknownHostException;

/// Anonymizes IP addresses before any persistence or audit write.
///
/// ## Rules
///
/// - **IPv4** — zeroes the last octet, retaining the `/24` prefix.
///   Example: `192.168.1.42` → `192.168.1.0`.
/// - **IPv6** — zeroes the last 80 bits, retaining the `/48` prefix.
///   Example: `2001:db8:cafe::1` → `2001:db8:cafe::`.
///
/// ## Where to apply
///
/// The primary adapter (`AddEndpoint`) applies this **before** building the
/// command object.  All downstream components (command handlers, repositories,
/// audit writers) therefore receive an already-anonymized address and need not
/// know the original IP.
///
/// ## Null / blank inputs
///
/// A `null` or blank input is returned as-is so that optional IP fields do not
/// require special handling by callers.
public final class IpAnonymizer {

    private IpAnonymizer() {}

    /// Returns an anonymized copy of the given IP address string.
    ///
    /// @param ipAddress raw IPv4 or IPv6 address; may be `null` or blank
    /// @return anonymized address, or the original value if `null`, blank,
    ///         or unparseable
    public static String anonymize(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return ipAddress;
        }
        if (ipAddress.contains(":")) {
            return anonymizeIpv6(ipAddress);
        }
        return anonymizeIpv4(ipAddress);
    }

    private static String anonymizeIpv4(String ip) {
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) {
            return ip;
        }
        return ip.substring(0, lastDot) + ".0";
    }

    private static String anonymizeIpv6(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte[] bytes = addr.getAddress();
            if (bytes.length != 16) {
                // Not a full IPv6 address — return as-is.
                return ip;
            }
            // Keep first 6 bytes (48 bits), zero the rest (80 bits).
            for (int i = 6; i < 16; i++) {
                bytes[i] = 0;
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
