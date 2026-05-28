/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyNotFoundException;
import io.github.bmarwell.keyserver.application.api.ex.KeyParsingException;
import io.github.bmarwell.keyserver.application.api.ex.KeyServerException;
import io.github.bmarwell.keyserver.application.api.ex.KeyValidationException;
import io.github.bmarwell.keyserver.application.api.ex.VerificationException;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/// JAX-RS exception mapper for the REST (`/rest/`) endpoint family.
///
/// Maps domain exceptions to RFC 7807 `application/problem+json` responses
/// without leaking server internals.  All internal detail is logged server-side only.
///
/// The `type` URI points to the corresponding `@GET` method on {@link Problems},
/// which serves a human-readable description of the problem type.
/// When {@link UriInfo} is unavailable (e.g. in unit tests), the `type` field
/// falls back to `urn:keyserver:error:{slug}`.
@Provider
public class KeyServerExceptionMapper implements ExceptionMapper<KeyServerException> {

    static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";

    private static final Logger LOG = Logger.getLogger(KeyServerExceptionMapper.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Context
    @Nullable
    UriInfo uriInfo;

    @Override
    public Response toResponse(KeyServerException ex) {
        int status = resolveStatus(ex);
        LOG.log(Level.WARNING, "KeyServer domain exception [correlationId={0}]: {1}", new Object[] {
            ex.getCorrelationId(), ex.getMessage()
        });
        if (ex.getCause() != null) {
            LOG.log(Level.FINE, "Caused by", ex.getCause());
        }

        String slug = problemSlug(ex);
        String typeUri = buildTypeUri(slug);
        Optional<String> instanceUri =
                this.uriInfo != null ? Optional.of(this.uriInfo.getRequestUri().toString()) : Optional.empty();
        Optional<String> fingerprint = ex.getFingerprint().map(fp -> fp.value());

        ProblemJson problem = new ProblemJson(
                typeUri, titleFor(status), status, detailFor(ex), instanceUri, ex.getCorrelationId(), fingerprint);

        return Response.status(status)
                .header("X-Correlation-ID", ex.getCorrelationId())
                .entity(JSONB.toJson(problem))
                .type(PROBLEM_JSON_MEDIA_TYPE)
                .build();
    }

    private String buildTypeUri(String slug) {
        if (this.uriInfo == null) {
            return "urn:keyserver:error:" + slug;
        }
        URI typeUri =
                this.uriInfo.getBaseUriBuilder().path(Problems.class).path(slug).build();
        return typeUri.toString();
    }

    static String problemSlug(KeyServerException ex) {
        return switch (ex) {
            case KeyNotFoundException ignored -> "key-not-found";
            case DuplicateKeyException ignored -> "duplicate-key";
            case KeyParsingException ignored -> "key-parsing";
            case KeyValidationException ignored -> "key-validation";
            case VerificationException ignored -> "verification-error";
        };
    }

    private static int resolveStatus(KeyServerException ex) {
        return switch (ex) {
            case KeyNotFoundException ignored -> 404;
            case DuplicateKeyException ignored -> 200;
            case KeyValidationException ignored -> 400;
            case KeyParsingException ignored -> 400;
            case VerificationException ignored -> 400;
        };
    }

    private static String titleFor(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            default -> "Internal Server Error";
        };
    }

    private static String detailFor(KeyServerException ex) {
        return switch (ex) {
            case KeyNotFoundException ignored -> "No key matching the supplied identifier exists in this keystore.";
            case DuplicateKeyException ignored -> "The submitted key is already present in the published keystore.";
            case KeyParsingException ignored -> "The submitted key material could not be parsed.";
            case KeyValidationException ignored -> "The submitted key did not pass validation.";
            case VerificationException ignored -> "The verification request could not be completed.";
        };
    }

    // CDI-context-friendly setter for unit testing without a container.
    void setUriInfo(@Nullable UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }
}
