/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyNotFoundException;
import io.github.bmarwell.keyserver.application.api.ex.KeyServerException;
import io.github.bmarwell.keyserver.application.api.ex.KeyValidationException;
import io.github.bmarwell.keyserver.application.api.ex.VerificationException;
import jakarta.json.Json;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/// JAX-RS exception mapper for the REST (`/rest/`) endpoint family.
///
/// Maps domain exceptions to JSON HTTP responses without leaking server internals.
/// All detail is logged server-side only.
@Provider
public class KeyServerExceptionMapper implements ExceptionMapper<KeyServerException> {

    private static final Logger LOG = Logger.getLogger(KeyServerExceptionMapper.class.getName());

    @Override
    public Response toResponse(KeyServerException ex) {
        int status = resolveStatus(ex);
        LOG.log(Level.WARNING, "KeyServer domain exception [correlationId={0}]: {1}", new Object[] {
            ex.getCorrelationId(), ex.getMessage()
        });
        if (ex.getCause() != null) {
            LOG.log(Level.FINE, "Caused by", ex.getCause());
        }

        var bodyBuilder =
                Json.createObjectBuilder().add("error", safeLabel(ex)).add("correlationId", ex.getCorrelationId());

        ex.getFingerprint().ifPresent(fp -> bodyBuilder.add("fingerprint", fp.value()));

        return Response.status(status)
                .header("X-Correlation-ID", ex.getCorrelationId())
                .entity(bodyBuilder.build().toString())
                .type("application/json")
                .build();
    }

    private static int resolveStatus(KeyServerException ex) {
        if (ex instanceof KeyNotFoundException) {
            return 404;
        }
        if (ex instanceof DuplicateKeyException) {
            return 200;
        }
        if (ex instanceof KeyValidationException || ex instanceof VerificationException) {
            return 400;
        }
        return 400;
    }

    private static String safeLabel(KeyServerException ex) {
        return ex.getClass().getSimpleName();
    }
}
