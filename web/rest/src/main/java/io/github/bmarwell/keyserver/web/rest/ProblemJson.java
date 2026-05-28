/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

import java.util.Optional;

/// Immutable value-type DTO representing an RFC 7807 Problem Details object.
///
/// Fields:
/// - `type` — a URI identifying the problem type; dereferences to a human-readable description
///   served by {@link Problems}
/// - `title` — the HTTP status reason phrase (e.g. "Not Found")
/// - `status` — the HTTP status code as an integer
/// - `detail` — a safe, fixed-text description of this occurrence; never contains internal detail
/// - `instance` — the request URI from {@code UriInfo}; {@link java.util.Optional#empty()} when
///   the URI is unavailable (e.g. no container context). JSON-B omits the field when empty.
/// - `correlationId` — opaque correlation ID for log correlation and the {@code X-Correlation-ID} header
/// - `fingerprint` — key fingerprint carried by the exception, if any. JSON-B omits the field when empty.
///
/// Serialised to JSON via JSON-B inside {@link KeyServerExceptionMapper}.
/// The Jakarta JSON-B specification treats {@code Optional.empty()} as absent and omits the field entirely —
/// no manual null-checks or annotations are needed.
///
/// Note: {@code Optional} is used for {@code instance} and {@code fingerprint} because they represent
/// genuinely absent-or-present fields. This is an intentional exception to the general guideline of
/// avoiding {@code Optional} fields — record components that map directly to optional JSON fields are
/// a recognised valid use case.
record ProblemJson(
        String type,
        String title,
        int status,
        String detail,
        Optional<String> instance,
        String correlationId,
        Optional<String> fingerprint) {}
