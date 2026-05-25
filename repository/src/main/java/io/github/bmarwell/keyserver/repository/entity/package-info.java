/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */

/// JPA entity classes for the keyserver persistence layer.
///
/// All types in this package are non-null by default (jspecify `@NullMarked`).
/// Optional / database-nullable columns are annotated with
/// {@link org.jspecify.annotations.Nullable} on the corresponding field and
/// every method parameter that accepts a `null` value.
@NullMarked
package io.github.bmarwell.keyserver.repository.entity;

import org.jspecify.annotations.NullMarked;
