/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class ProblemsTest {

    private final Problems problems = new Problems();

    @Test
    void keyNotFound_returns200WithPlainText() {
        // given / when
        Response response = this.problems.keyNotFound();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("Problems pages must be served as text/plain")
                .startsWith("text/plain");
        assertThat((String) response.getEntity()).contains("Key Not Found").contains("404");
    }

    @Test
    void duplicateKey_returns200WithPlainText() {
        // given / when
        Response response = this.problems.duplicateKey();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("Problems pages must be served as text/plain")
                .startsWith("text/plain");
        assertThat((String) response.getEntity()).contains("Duplicate Key").contains("200");
    }

    @Test
    void keyParsing_returns200WithPlainText() {
        // given / when
        Response response = this.problems.keyParsing();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("Problems pages must be served as text/plain")
                .startsWith("text/plain");
        assertThat((String) response.getEntity()).contains("Key Parsing").contains("400");
    }

    @Test
    void keyValidation_returns200WithPlainText() {
        // given / when
        Response response = this.problems.keyValidation();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("Problems pages must be served as text/plain")
                .startsWith("text/plain");
        assertThat((String) response.getEntity()).contains("Key Validation").contains("400");
    }

    @Test
    void verificationError_returns200WithPlainText() {
        // given / when
        Response response = this.problems.verificationError();

        // then
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("Content-Type"))
                .as("Problems pages must be served as text/plain")
                .startsWith("text/plain");
        assertThat((String) response.getEntity()).contains("Verification").contains("400");
    }
}
