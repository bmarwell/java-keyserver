/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.pks;

import freemarker.template.Template;
import java.io.IOException;

public interface FreemarkerConfiguration {

    Template getTemplate(String templateName) throws IOException;
}
