/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Unit tests for {@link VerifyRenderer}.
///
/// Verifies that each page type contains the expected DOCTYPE, charset, and content
/// without relying on a CDI container.
class VerifyRendererTest {

    VerifyRenderer renderer;

    @BeforeEach
    void setUp() {
        this.renderer = new VerifyRenderer();
    }

    @Test
    void success_page_contains_doctype_and_charset() {
        // given
        var result = new VerificationResult("Alice <alice@example.com>", "AABBCCDDEEFF00112233445566778899AABBCCDD");

        // when
        String html = this.renderer.renderSuccess(result);

        // then
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("charset=\"utf-8\"");
    }

    @Test
    void success_page_contains_uid_and_fingerprint() {
        // given
        var result = new VerificationResult("Alice <alice@example.com>", "AABBCCDDEEFF00112233445566778899AABBCCDD");

        // when
        String html = this.renderer.renderSuccess(result);

        // then
        assertThat(html).contains("Alice &lt;alice@example.com&gt;");
        assertThat(html).contains("AABBCCDDEEFF00112233445566778899AABBCCDD");
    }

    @Test
    void success_page_escapes_special_chars_in_uid() {
        // given — UID contains angle brackets (common in PGP UIDs)
        var result = new VerificationResult("Bob & Alice <bob&alice@example.com>", "FFFF");

        // when
        String html = this.renderer.renderSuccess(result);

        // then — raw < > & must not appear unescaped inside the content
        assertThat(html).doesNotContain("Bob & Alice <bob&alice@example.com>");
        assertThat(html).contains("Bob &amp; Alice &lt;bob&amp;alice@example.com&gt;");
    }

    @Test
    void expired_page_contains_doctype_and_charset() {
        // given — no input required

        // when
        String html = this.renderer.renderExpired();

        // then
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("charset=\"utf-8\"");
        assertThat(html).contains("expired");
    }

    @Test
    void invalid_page_contains_doctype_and_charset() {
        // given — no input required

        // when
        String html = this.renderer.renderInvalid();

        // then
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("charset=\"utf-8\"");
        assertThat(html).containsAnyOf("invalid", "Invalid");
    }
}
