/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyNotFoundException;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.application.api.ex.TokenExpiredException;
import io.github.bmarwell.keyserver.application.api.ex.TooManyVerifiableUidsException;
import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeyServerExceptionMapperTest {

    private static final String BASE_URI = "http://localhost/rest/";

    private final KeyServerExceptionMapper mapper = new KeyServerExceptionMapper();

    @BeforeEach
    void setUp() {
        this.mapper.setUriInfo(uriInfoFor(BASE_URI, BASE_URI + "some-repo/key/abc"));
    }

    // -------------------------------------------------------------------------
    // Status code mapping
    // -------------------------------------------------------------------------

    @Test
    void keyNotFound_returns404() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("not there");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getStatus())
                .as("KeyNotFoundException must map to 404 Not Found")
                .isEqualTo(404);
    }

    @Test
    void duplicateKey_returns200() {
        // given
        DuplicateKeyException ex = new DuplicateKeyException("already stored", fingerprint("abc123"));

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getStatus())
                .as("DuplicateKeyException must map to 200 OK (idempotent)")
                .isEqualTo(200);
    }

    @Test
    void keyParsing_returns400() {
        // given
        KeyParsingException ex = new KeyParsingException("bad armor");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void keyValidation_returns400() {
        // given
        TooManyVerifiableUidsException ex = new TooManyVerifiableUidsException("too many");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void verificationError_returns400() {
        // given
        TokenExpiredException ex = new TokenExpiredException("expired");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getStatus()).isEqualTo(400);
    }

    // -------------------------------------------------------------------------
    // Content-Type and headers
    // -------------------------------------------------------------------------

    @Test
    void response_hasProblemJsonContentType() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getHeaderString("Content-Type"))
                .as("REST errors must use application/problem+json per RFC 7807")
                .startsWith("application/problem+json");
    }

    @Test
    void response_hasCorrelationIdHeader() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        Response response = this.mapper.toResponse(ex);

        // then
        assertThat(response.getHeaderString("X-Correlation-ID"))
                .as("X-Correlation-ID header must match the exception's correlationId")
                .isEqualTo(ex.getCorrelationId());
    }

    // -------------------------------------------------------------------------
    // RFC 7807 JSON body fields
    // -------------------------------------------------------------------------

    @Test
    void body_containsAllRfc7807Fields() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.containsKey("type"))
                .as("RFC 7807 requires 'type' field")
                .isTrue();
        assertThat(body.containsKey("title"))
                .as("this keyserver includes 'title' field (strongly recommended by RFC 7807)")
                .isTrue();
        assertThat(body.containsKey("status"))
                .as("this keyserver includes 'status' field (strongly recommended by RFC 7807)")
                .isTrue();
        assertThat(body.containsKey("detail"))
                .as("this keyserver includes 'detail' field (optional per RFC 7807)")
                .isTrue();
        assertThat(body.containsKey("instance"))
                .as("instance must be included when the request URI is available")
                .isTrue();
        assertThat(body.containsKey("correlationId"))
                .as("extension field 'correlationId' must be present")
                .isTrue();
    }

    @Test
    void body_typeUriPointsToProblemsEndpoint() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("type"))
                .as("type URI must point to the Problems endpoint under the base URI")
                .startsWith(BASE_URI + "problems/")
                .endsWith("key-not-found");
    }

    @Test
    void body_typeUriUsesUrnFallbackWhenUriInfoIsNull() {
        // given
        this.mapper.setUriInfo(null);
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("type"))
                .as("fallback type when UriInfo is absent must be a urn:keyserver:error URN")
                .isEqualTo("urn:keyserver:error:key-not-found");
        assertThat(body.containsKey("instance"))
                .as("instance must be absent when UriInfo is not available")
                .isFalse();
    }

    @Test
    void body_titleIsHttpReasonPhrase() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("title"))
                .as("title must be the HTTP status reason phrase")
                .isEqualTo("Not Found");
    }

    @Test
    void body_statusMatchesHttpStatusCode() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getInt("status")).isEqualTo(404);
    }

    @Test
    void body_detailDoesNotLeakInternalMessage() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("super sensitive internal message");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("detail"))
                .as("detail must not contain the raw exception message")
                .doesNotContain("super sensitive internal message");
    }

    @Test
    void body_instanceIsRequestUri() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("instance"))
                .as("instance must reflect the actual request URI from UriInfo")
                .isEqualTo(BASE_URI + "some-repo/key/abc");
    }

    @Test
    void body_correlationIdMatchesException() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("correlationId"))
                .as("correlationId in JSON body must match the exception's correlationId")
                .isEqualTo(ex.getCorrelationId());
    }

    @Test
    void body_includesFingerprintWhenPresent() {
        // given
        String fpValue = "0123456789abcdef0123456789abcdef01234567";
        DuplicateKeyException ex = new DuplicateKeyException("already present", fingerprint(fpValue));

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.getString("fingerprint"))
                .as("fingerprint must be included when the exception carries one")
                .isEqualTo(fpValue);
    }

    @Test
    void body_hasNoFingerprintFieldWhenAbsent() {
        // given
        KeyNotFoundException ex = new KeyNotFoundException("missing without fingerprint");

        // when
        JsonObject body = parseBody(this.mapper.toResponse(ex));

        // then
        assertThat(body.containsKey("fingerprint"))
                .as("fingerprint field must be absent when the exception has none")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Problem slug coverage
    // -------------------------------------------------------------------------

    @Test
    void slugMapping_coversAllExceptionFamilies() {
        assertThat(KeyServerExceptionMapper.problemSlug(new KeyNotFoundException("x")))
                .isEqualTo("key-not-found");
        assertThat(KeyServerExceptionMapper.problemSlug(new DuplicateKeyException("x", fingerprint("a"))))
                .isEqualTo("duplicate-key");
        assertThat(KeyServerExceptionMapper.problemSlug(new KeyParsingException("x")))
                .isEqualTo("key-parsing");
        assertThat(KeyServerExceptionMapper.problemSlug(new TooManyVerifiableUidsException("x")))
                .isEqualTo("key-validation");
        assertThat(KeyServerExceptionMapper.problemSlug(new TokenExpiredException("x")))
                .isEqualTo("verification-error");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static JsonObject parseBody(Response response) {
        String entity = (String) response.getEntity();
        return Json.createReader(new StringReader(entity)).readObject();
    }

    private static KeyFingerprint fingerprint(String value) {
        return new KeyFingerprint() {
            @Override
            public String value() {
                return value;
            }
        };
    }

    private static UriInfo uriInfoFor(String baseUri, String requestUri) {
        return new UriInfo() {
            @Override
            public URI getBaseUri() {
                return URI.create(baseUri);
            }

            @Override
            public UriBuilder getBaseUriBuilder() {
                return UriBuilder.fromUri(baseUri);
            }

            @Override
            public URI getRequestUri() {
                return URI.create(requestUri);
            }

            @Override
            public UriBuilder getRequestUriBuilder() {
                return UriBuilder.fromUri(requestUri);
            }

            @Override
            public URI getAbsolutePath() {
                return URI.create(requestUri);
            }

            @Override
            public UriBuilder getAbsolutePathBuilder() {
                return UriBuilder.fromUri(requestUri);
            }

            @Override
            public String getPath() {
                return URI.create(requestUri).getPath();
            }

            @Override
            public String getPath(boolean decode) {
                return getPath();
            }

            @Override
            public List<PathSegment> getPathSegments() {
                return List.of();
            }

            @Override
            public List<PathSegment> getPathSegments(boolean decode) {
                return List.of();
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters() {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getPathParameters(boolean decode) {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters() {
                return null;
            }

            @Override
            public MultivaluedMap<String, String> getQueryParameters(boolean decode) {
                return null;
            }

            @Override
            public List<String> getMatchedURIs() {
                return List.of();
            }

            @Override
            public List<String> getMatchedURIs(boolean decode) {
                return List.of();
            }

            @Override
            public List<Object> getMatchedResources() {
                return List.of();
            }

            @Override
            public URI resolve(URI uri) {
                return URI.create(baseUri).resolve(uri);
            }

            @Override
            public URI relativize(URI uri) {
                return URI.create(baseUri).relativize(uri);
            }

            @Override
            public String getMatchedResourceTemplate() {
                return "";
            }
        };
    }
}
