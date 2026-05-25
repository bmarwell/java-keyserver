/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids.internal;

import io.github.bmarwell.keyserver.common.ids.KeyId;

public record KeyIdImplementation(String value) implements KeyId {}
