/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class KeyServerExceptionMapperTest {

    private final KeyServerExceptionMapper mapper = new KeyServerExceptionMapper();

    @Test
    void returnsTextPlainBodyForExceptionWithoutFingerprint() {
        KeyParsingException ex = new KeyParsingException("bad key format");

        Response response = mapper.toResponse(ex);
        String body = (String) response.getEntity();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeaderString("X-Correlation-ID")).isEqualTo(ex.getCorrelationId());
        assertThat(response.getHeaderString("Content-Type")).startsWith("text/plain");
        assertThat(body).isEqualTo("KeyParsingException: [correlationId: " + ex.getCorrelationId() + "]");
        assertThat(body).doesNotStartWith("{");
    }

    @Test
    void includesFingerprintInTextBodyWhenAvailable() {
        String fingerprintValue = "0123456789abcdef0123456789abcdef01234567";
        DuplicateKeyException ex = new DuplicateKeyException("duplicate", new TestFingerprint(fingerprintValue));

        Response response = mapper.toResponse(ex);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaderString("X-Correlation-ID")).isEqualTo(ex.getCorrelationId());
        assertThat(response.getHeaderString("Content-Type")).startsWith("text/plain");
        assertThat(response.getEntity())
                .isEqualTo("DuplicateKeyException: " + fingerprintValue + " [correlationId: " + ex.getCorrelationId()
                        + "]");
    }

    private record TestFingerprint(String value) implements KeyFingerprint {}
}
