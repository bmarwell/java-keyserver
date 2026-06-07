/*
 * Copyright (C) 2023-2026 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.tsid;

import io.hypersistence.tsid.TSID;

public interface TsidFactory {

    TSID generate();
}
