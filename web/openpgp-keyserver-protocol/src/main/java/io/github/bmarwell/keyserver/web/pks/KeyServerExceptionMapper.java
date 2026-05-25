/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import io.github.bmarwell.keyserver.application.api.ex.DuplicateKeyException;
import io.github.bmarwell.keyserver.application.api.ex.KeyNotFoundException;
import io.github.bmarwell.keyserver.application.api.ex.KeyServerException;
import io.github.bmarwell.keyserver.application.api.ex.KeyValidationException;
import io.github.bmarwell.keyserver.application.api.ex.VerificationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/// JAX-RS exception mapper for the HKP (`/pks/`) endpoint family.
///
/// Maps domain exceptions to HTTP responses without leaking server internals.
/// All detail is logged server-side; the response body contains only what the
/// caller could already know (correlation ID, safe error label, optional fingerprint).
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

        String fingerprintPart = ex.getFingerprint().map(fp -> " " + fp.value()).orElse("");
        String body = safeLabel(ex) + ":" + fingerprintPart + " [correlationId: " + ex.getCorrelationId() + "]";

        return Response.status(status)
                .header("X-Correlation-ID", ex.getCorrelationId())
                .entity(body)
                .type("text/plain")
                .build();
    }

    private static int resolveStatus(KeyServerException ex) {
        if (ex instanceof KeyNotFoundException) {
            return 404;
        }
        if (ex instanceof DuplicateKeyException) {
            // idempotent — treat as success
            return 200;
        }
        if (ex instanceof KeyValidationException || ex instanceof VerificationException) {
            return 400;
        }
        // KeyParsingException and anything unexpected
        return 400;
    }

    private static String safeLabel(KeyServerException ex) {
        // Return only the simple class name — safe, generic, no internals
        return ex.getClass().getSimpleName();
    }
}
