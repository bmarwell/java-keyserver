/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import jakarta.json.Json;
import jakarta.json.stream.JsonParsingException;
import jakarta.ws.rs.core.Response;
import java.io.StringReader;
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
        assertThatThrownBy(() -> Json.createReader(new StringReader(body)).readValue())
                .isInstanceOf(JsonParsingException.class);
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
