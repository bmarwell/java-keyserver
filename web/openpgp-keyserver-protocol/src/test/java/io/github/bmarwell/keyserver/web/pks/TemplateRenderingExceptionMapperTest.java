/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class TemplateRenderingExceptionMapperTest {

    private final TemplateRenderingExceptionMapper mapper = new TemplateRenderingExceptionMapper();

    @Test
    void returns500ForTemplateRenderingFailure() {
        // given
        TemplateRenderingException ex =
                new TemplateRenderingException("some-template.ftlh", new RuntimeException("boom"));

        // when
        Response response = mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaderString("Content-Type")).startsWith("text/html");
    }

    @Test
    void responseBodyIsHardcodedHtmlNotFromTemplate() {
        // given
        TemplateRenderingException ex =
                new TemplateRenderingException("broken.ftlh", new IllegalStateException("template broken"));

        // when
        String body = (String) mapper.toResponse(ex).getEntity();

        // then — body must be present but must not contain the exception message (no internal leak)
        assertThat(body).startsWith("<!DOCTYPE html>");
        assertThat(body).contains("Internal Server Error");
        assertThat(body).doesNotContain("template broken");
        assertThat(body).doesNotContain("broken.ftlh");
    }
}
