/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

/// Thrown when a Freemarker template cannot be loaded or fails during processing.
///
/// This is an infrastructure-level failure (missing or broken template file, bad
/// template syntax, unexpected model value), not a domain exception.  It is caught
/// by {@link TemplateRenderingExceptionMapper}, which logs the detail server-side
/// and returns a generic HTTP 500 response to the caller.
class TemplateRenderingException extends RuntimeException {

    /// @param templateName the name of the template that failed (included in the log)
    /// @param cause        the underlying Freemarker or I/O exception
    TemplateRenderingException(String templateName, Exception cause) {
        super("Failed to render template: " + templateName, cause);
    }
}
