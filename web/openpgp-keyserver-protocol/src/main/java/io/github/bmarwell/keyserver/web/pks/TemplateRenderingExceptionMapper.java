/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
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
///   <li>Negotiates the response content type from the request's {@code Accept} header:
///       HTML browsers receive a minimal hard-coded HTML error page; plain-text clients
///       (e.g. HKP command-line tools) receive a one-line plain-text message.</li>
///   <li>Never uses the Freemarker template engine for the error response — a broken
///       template cannot cause a recursive rendering failure.</li>
/// </ol>
@Provider
@Produces({MediaType.TEXT_HTML, MediaType.TEXT_PLAIN})
class TemplateRenderingExceptionMapper implements ExceptionMapper<TemplateRenderingException> {

    private static final Logger LOG = Logger.getLogger(TemplateRenderingExceptionMapper.class.getName());

    private static final String HTML_ERROR_BODY = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>Internal Server Error</title></head>
            <body>
            <h1>Internal Server Error</h1>
            <p>An unexpected error occurred while generating the response. Please try again later.</p>
            </body>
            </html>
            """;

    private static final String PLAIN_ERROR_BODY = "Internal Server Error\n"
            + "An unexpected error occurred while generating the response."
            + " Please try again later.\n";

    @Context
    HttpHeaders httpHeaders;

    @Override
    public Response toResponse(TemplateRenderingException ex) {
        LOG.log(Level.SEVERE, "Template rendering failed: {0}", ex.getMessage());
        LOG.log(Level.SEVERE, "Caused by", ex.getCause());
        if (prefersPlainText()) {
            return Response.serverError()
                    .entity(PLAIN_ERROR_BODY)
                    .type("text/plain;charset=utf-8")
                    .build();
        }
        return Response.serverError()
                .entity(HTML_ERROR_BODY)
                .type("text/html;charset=utf-8")
                .build();
    }

    private boolean prefersPlainText() {
        if (this.httpHeaders == null) {
            return false;
        }
        List<MediaType> acceptable = this.httpHeaders.getAcceptableMediaTypes();
        // Walk Accept types in client-declared priority order.
        // Return plain text only if text/plain is listed before text/html (or */*).
        for (MediaType mt : acceptable) {
            if (mt.isCompatible(MediaType.TEXT_HTML_TYPE)) {
                return false;
            }
            if (mt.isCompatible(MediaType.TEXT_PLAIN_TYPE)) {
                return true;
            }
        }
        return false;
    }

    // JAX-RS-context-friendly setter for unit testing without a container.
    void setHttpHeaders(HttpHeaders httpHeaders) {
        this.httpHeaders = httpHeaders;
    }
}
