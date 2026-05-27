/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import static org.assertj.core.api.Assertions.assertThat;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// Smoke-tests for {@link FreemarkerConfiguration}.
///
/// Verifies that the produced {@link Configuration} can load a template from the
/// classpath and render it without errors, validating the template-loader and
/// encoding setup without a CDI container.
class FreemarkerConfigurationTest {

    @Test
    void configuration_loads_and_renders_smoke_template() throws Exception {
        // given
        Configuration cfg = new FreemarkerConfiguration().freemarkerConfiguration();

        // when
        Template template = cfg.getTemplate("smoke-test.ftlh");
        StringWriter out = new StringWriter();
        template.process(Map.of("name", "world"), out);

        // then
        assertThat(out.toString()).isEqualTo("Hello, world!");
    }

    @Test
    void configuration_html_escapes_user_values() throws Exception {
        // given — the .ftlh extension triggers HTML auto-escaping
        Configuration cfg = new FreemarkerConfiguration().freemarkerConfiguration();

        // when
        Template template = cfg.getTemplate("smoke-test.ftlh");
        StringWriter out = new StringWriter();
        template.process(Map.of("name", "<script>"), out);

        // then — angle brackets must be escaped; raw HTML must not reach the output
        assertThat(out.toString()).doesNotContain("<script>");
        assertThat(out.toString()).contains("&lt;script&gt;");
    }
}
