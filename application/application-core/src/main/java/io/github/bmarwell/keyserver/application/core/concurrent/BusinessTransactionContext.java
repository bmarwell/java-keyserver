/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core.concurrent;

import jakarta.enterprise.context.RequestScoped;

/// Carries the current business-transaction ID for the duration of one command execution.
///
/// Jakarta Concurrency 3.1 propagates the CDI request context to async-managed threads,
/// so each command invocation gets its own fresh `@RequestScoped` instance.
/// Components that need the BTX ID (e.g. `AuditLogService`) inject this bean
/// rather than receiving it as a method argument.
@RequestScoped
public class BusinessTransactionContext {

    private long btxId;
    private boolean initialized;

    /// Sets the BTX ID.  Must be called exactly once per request, by `KeyServerCommandService`,
    /// before any handler code runs.
    public void initialize(long btxId) {
        if (initialized) {
            throw new IllegalStateException("BusinessTransactionContext already initialized");
        }
        this.btxId = btxId;
        this.initialized = true;
    }

    public long getBtxId() {
        if (!initialized) {
            throw new IllegalStateException("BusinessTransactionContext not yet initialized");
        }
        return btxId;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
