/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRenderingExceptionMapperTest {

    private final TemplateRenderingExceptionMapper mapper = new TemplateRenderingExceptionMapper();

    @Test
    void returns500WithHtmlByDefault() {
        // given — no HttpHeaders injected (null), simulating no JAX-RS container
        TemplateRenderingException ex =
                new TemplateRenderingException("some-template.ftlh", new RuntimeException("boom"));

        // when
        Response response = mapper.toResponse(ex);

        // then
        assertThat(response.getStatus())
                .as("template rendering failure must yield 500 Internal Server Error")
                .isEqualTo(500);
        assertThat(response.getHeaderString("Content-Type"))
                .as("default response must be HTML when no Accept header is available")
                .startsWith("text/html");
    }

    @Test
    void htmlBodyIsHardcodedNotFromTemplate() {
        // given
        TemplateRenderingException ex =
                new TemplateRenderingException("broken.ftlh", new IllegalStateException("template broken"));

        // when
        String body = (String) mapper.toResponse(ex).getEntity();

        // then
        assertThat(body).as("response must start with a valid HTML DOCTYPE").startsWith("<!DOCTYPE html>");
        assertThat(body).as("body must contain a human-readable error heading").contains("Internal Server Error");
        assertThat(body)
                .as("internal exception message must not leak to the caller")
                .doesNotContain("template broken");
        assertThat(body).as("template name must not leak to the caller").doesNotContain("broken.ftlh");
    }

    @Test
    void returnsPlainTextWhenClientPrefersIt() {
        // given — client sends Accept: text/plain (e.g. a GnuPG HKP request)
        mapper.setHttpHeaders(acceptOnly(MediaType.TEXT_PLAIN_TYPE));
        TemplateRenderingException ex =
                new TemplateRenderingException("some-template.ftl", new RuntimeException("oops"));

        // when
        Response response = mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaderString("Content-Type"))
                .as("client that prefers text/plain must receive text/plain response")
                .startsWith("text/plain");
        assertThat((String) response.getEntity())
                .as("plain-text body must not contain HTML tags")
                .doesNotContain("<html>");
    }

    @Test
    void returnsHtmlWhenClientPrefersHtml() {
        // given — client sends Accept: text/html (e.g. a browser)
        mapper.setHttpHeaders(acceptOnly(MediaType.TEXT_HTML_TYPE));
        TemplateRenderingException ex =
                new TemplateRenderingException("some-template.ftlh", new RuntimeException("oops"));

        // when
        Response response = mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaderString("Content-Type"))
                .as("client that prefers text/html must receive text/html response")
                .startsWith("text/html");
        assertThat((String) response.getEntity()).startsWith("<!DOCTYPE html>");
    }

    // ---------------------------------------------------------------------------
    // Minimal HttpHeaders stub — only implements getAcceptableMediaTypes()
    // ---------------------------------------------------------------------------

    private static HttpHeaders acceptOnly(MediaType type) {
        return new HttpHeaders() {
            @Override
            public List<MediaType> getAcceptableMediaTypes() {
                return List.of(type);
            }

            @Override
            public List<String> getRequestHeader(String name) {
                return List.of();
            }

            @Override
            public String getHeaderString(String name) {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getRequestHeaders() {
                return null;
            }

            @Override
            public List<Locale> getAcceptableLanguages() {
                return List.of();
            }

            @Override
            public MediaType getMediaType() {
                return null;
            }

            @Override
            public Locale getLanguage() {
                return null;
            }

            @Override
            public Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
                return Map.of();
            }

            @Override
            public Date getDate() {
                return null;
            }

            @Override
            public int getLength() {
                return -1;
            }

            @Override
            public boolean containsHeaderString(
                    String name, String valueSeparatorRegex, java.util.function.Predicate<String> valuePredicate) {
                return false;
            }
        };
    }
}
