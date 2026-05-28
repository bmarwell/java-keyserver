/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.web.rest;

/// Immutable value-type DTO representing an RFC 7807 Problem Details object.
///
/// Fields:
/// - `type` — a URI identifying the problem type; dereferences to a human-readable description
///   served by {@link Problems}
/// - `title` — the HTTP status reason phrase (e.g. "Not Found")
/// - `status` — the HTTP status code as an integer
/// - `detail` — a safe, fixed-text description of this occurrence; never contains internal detail
/// - `instance` — the request URI from {@code UriInfo}; absent when the URI is unavailable
/// - `correlationId` — opaque correlation ID for log correlation and the {@code X-Correlation-ID} header
///
/// Serialised to a JSON string via JSON-P inside {@link KeyServerExceptionMapper}
/// (no JSON-B annotations required).
record ProblemJson(String type, String title, int status, String detail, String instance, String correlationId) {}
