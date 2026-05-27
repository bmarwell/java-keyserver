/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.VerificationResult;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TokenInvalidException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Unit tests for {@link VerifyEndpoint}.
///
/// Verifies that the endpoint maps service outcomes to the correct HTTP status codes
/// and HTML content-type without a CDI container or server.
class VerifyEndpointTest {

    static final String FINGERPRINT = "AABBCCDDEEFF00112233445566778899AABBCCDD";

    VerifyEndpoint endpoint;

    @BeforeEach
    void setUp() {
        VerifyRenderer renderer = new VerifyRenderer();
        renderer.setConfiguration(new FreemarkerConfiguration().freemarkerConfiguration());
        this.endpoint = new VerifyEndpoint();
        this.endpoint.setVerifyRenderer(renderer);
    }

    @Test
    void valid_token_returns_200_with_html() {
        // given
        this.endpoint.setVerificationService(token -> new VerificationResult("Alice <alice@example.com>", FINGERPRINT));

        // when
        Response response = this.endpoint.verify("1000");

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getMediaType().toString()).startsWith("text/html");
        assertThat(response.getEntity().toString()).contains("verified");
    }

    @Test
    void valid_token_html_contains_uid() {
        // given
        this.endpoint.setVerificationService(token -> new VerificationResult("Alice <alice@example.com>", FINGERPRINT));

        // when
        Response response = this.endpoint.verify("1000");

        // then
        assertThat(response.getEntity().toString()).contains("Alice");
    }

    @Test
    void expired_token_returns_400_with_html() {
        // given
        this.endpoint.setVerificationService(token -> {
            throw new TokenExpiredException("expired");
        });

        // when
        Response response = this.endpoint.verify("1000");

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).startsWith("text/html");
        assertThat(response.getEntity().toString()).contains("expired");
    }

    @Test
    void invalid_token_returns_400_with_html() {
        // given
        this.endpoint.setVerificationService(token -> {
            throw new TokenInvalidException("not found");
        });

        // when
        Response response = this.endpoint.verify("not-a-number");

        // then
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMediaType().toString()).startsWith("text/html");
        assertThat(response.getEntity().toString()).containsAnyOf("invalid", "Invalid");
    }

    @Test
    void response_does_not_echo_token_in_body() {
        // given — a garbled token that must not appear in the error page body
        String garbledToken = "evil-token-12345";
        this.endpoint.setVerificationService(token -> {
            throw new TokenInvalidException("not found");
        });

        // when
        Response response = this.endpoint.verify(garbledToken);

        // then
        assertThat(response.getEntity().toString()).doesNotContain(garbledToken);
    }
}
