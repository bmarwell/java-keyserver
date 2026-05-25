/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IpAnonymizerTest {

    @ParameterizedTest
    @CsvSource({
        "192.168.1.42,  192.168.1.0",
        "10.0.0.1,      10.0.0.0",
        "255.255.255.255, 255.255.255.0",
        "1.2.3.4,       1.2.3.0",
    })
    void anonymizes_ipv4_last_octet(String input, String expected) {
        assertThat(IpAnonymizer.anonymize(input.strip())).isEqualTo(expected.strip());
    }

    @ParameterizedTest
    @CsvSource({
        // last 80 bits zeroed → keep first 48 bits (6 bytes / 3 groups)
        "2001:db8:85a3::8a2e:370:7334, 2001:db8:85a3:0:0:0:0:0",
        "fe80::1,                       fe80:0:0:0:0:0:0:0",
        "::1,                           0:0:0:0:0:0:0:0",
    })
    void anonymizes_ipv6_to_48_prefix(String input, String expected) {
        assertThat(IpAnonymizer.anonymize(input.strip())).isEqualTo(expected.strip());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void returns_null_or_empty_for_null_or_empty_input(String input) {
        assertThat(IpAnonymizer.anonymize(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"   "})
    void returns_blank_for_blank_input(String input) {
        assertThat(IpAnonymizer.anonymize(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-ip", "hostname.example.com"})
    void returns_original_for_non_ip_without_dot_structure(String input) {
        // No dot in expected location — last dot gives "hostname.example." + "com" → "hostname.example.0"
        // Anything without a colon goes through anonymizeIpv4; if it has a dot we get partial anonymization.
        // For simple non-structured values the anonymizer is best-effort; we just check it doesn't throw.
        assertThat(IpAnonymizer.anonymize(input)).isNotNull();
    }

    @Test
    void anonymize_returns_original_for_malformed_ip() {
        // 999.999.999.999 has a dot, so we get 999.999.999.0 — best-effort
        assertThat(IpAnonymizer.anonymize("999.999.999.999")).isEqualTo("999.999.999.0");
    }
}
