/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.IOException;

public record FreemarkerConfigurationDelegate(Configuration configuration) implements FreemarkerConfiguration {

    @Override
    public Template getTemplate(String templateName) throws IOException {
        return this.configuration.getTemplate(templateName);
    }
}
