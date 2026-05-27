/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import freemarker.cache.ClassTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.PlainTextOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/// CDI producer for the singleton Freemarker {@link Configuration}.
///
/// A single {@link Configuration} instance is safe to share across all threads once
/// built; Freemarker's own documentation mandates exactly this pattern.
///
/// Template loading strategy:
/// <ul>
///   <li>Templates are loaded from the classpath under {@code /templates/}.
///       Placing them in {@code src/main/resources/templates/} ensures they are
///       packaged inside the WAR's {@code WEB-INF/classes/templates/} directory.</li>
///   <li>{@code .ftlh} files (used for HTML pages) have auto-escaping enabled via
///       {@link HTMLOutputFormat} so raw user-supplied strings cannot inject HTML.</li>
///   <li>{@code .ftl} files (used for machine-readable HKP output) use
///       {@link PlainTextOutputFormat} — no escaping is applied, and the Java layer
///       must pre-encode all values before passing them to the model.</li>
/// </ul>
///
/// Exceptions thrown during template processing propagate as {@link RuntimeException}
/// (via {@link TemplateExceptionHandler#RETHROW_HANDLER}), which JAX-RS maps to a
/// 500 response via the existing {@link KeyServerExceptionMapper}.
@ApplicationScoped
public class FreemarkerConfiguration {

    /// Produces the shared Freemarker {@link Configuration}.
    ///
    /// @return configured and ready-to-use Freemarker configuration
    @Produces
    @ApplicationScoped
    public Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setTemplateLoader(new ClassTemplateLoader(FreemarkerConfiguration.class, "/templates/"));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        cfg.setRecognizeStandardFileExtensions(true);
        // Use "computer" number format to prevent locale-dependent grouping separators
        // (e.g. "10,000" in en-US) from appearing in template output.  Raw numeric
        // model values are rare — most are pre-converted to String in Java — but this
        // acts as a safety net for future templates.
        cfg.setNumberFormat("computer");
        return cfg;
    }
}
