/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.util.Map;

/// Base class for Freemarker-backed renderers.
///
/// Holds the shared {@link Configuration} injection, the {@link #processTemplate} helper,
/// and the CDI-friendly setter used to wire the configuration in unit tests without a
/// CDI container.
///
/// Subclasses are responsible only for building the template model and choosing the
/// template name; all template loading and rendering is handled here.
abstract class AbstractFreemarkerRenderer {

    @Inject
    Configuration configuration;

    /// Loads and processes a Freemarker template, returning the rendered string.
    ///
    /// @param templateName filename relative to the {@code /templates/} classpath root
    ///                     (e.g. {@code "verify-success.ftlh"})
    /// @param model        variables made available inside the template
    /// @return rendered output as a string
    /// @throws IllegalStateException if the template cannot be found or rendering fails
    protected String processTemplate(String templateName, Map<String, Object> model) {
        try {
            Template template = this.configuration.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render template: " + templateName, e);
        }
    }

    /// CDI-friendly setter; allows unit tests to inject a {@link Configuration} instance
    /// without spinning up a CDI container.
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }
}
