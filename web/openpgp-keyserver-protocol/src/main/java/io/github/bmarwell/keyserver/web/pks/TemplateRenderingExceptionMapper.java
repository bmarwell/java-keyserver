/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/// JAX-RS exception mapper for {@link TemplateRenderingException}.
///
/// Template rendering failures are infrastructure errors — a missing or broken
/// template file, a bad template syntax issue, or an unexpected model value.
/// They are unrelated to any client request and must never expose internal stack
/// traces to callers.
///
/// This mapper:
/// <ol>
///   <li>Logs the full exception at {@link Level#SEVERE} for operator investigation.</li>
///   <li>Returns a plain {@code 500 Internal Server Error} with a minimal HTML body
///       so the browser receives a human-readable page without risking a second
///       rendering failure (the body is a hardcoded string, not a template).</li>
/// </ol>
@Provider
class TemplateRenderingExceptionMapper implements ExceptionMapper<TemplateRenderingException> {

    private static final Logger LOG = Logger.getLogger(TemplateRenderingExceptionMapper.class.getName());

    private static final String ERROR_BODY = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>Internal Server Error</title></head>
            <body>
            <h1>Internal Server Error</h1>
            <p>An unexpected error occurred while generating the response. Please try again later.</p>
            </body>
            </html>
            """;

    @Override
    public Response toResponse(TemplateRenderingException ex) {
        LOG.log(Level.SEVERE, "Template rendering failed: {0}", ex.getMessage());
        LOG.log(Level.SEVERE, "Caused by", ex.getCause());
        return Response.serverError()
                .entity(ERROR_BODY)
                .type("text/html;charset=utf-8")
                .build();
    }
}
