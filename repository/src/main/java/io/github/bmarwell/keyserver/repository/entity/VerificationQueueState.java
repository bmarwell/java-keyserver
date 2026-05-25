/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository.entity;

/// State of a single UID verification entry.
public enum VerificationQueueState {
    PENDING,
    VERIFIED,
    EXPIRED,
    REJECTED
}
