/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository.entity;

/// State of a business transaction.
///
/// `STARTED` is written synchronously before the command handler runs,
/// in a `REQUIRES_NEW` JTA transaction, so it is always durable.
/// `COMPLETED` and `FAILED` are written afterwards — also in `REQUIRES_NEW`
/// — so they survive a rollback of the command's own transaction.
public enum BusinessTransactionState {
    STARTED,
    COMPLETED,
    FAILED
}
