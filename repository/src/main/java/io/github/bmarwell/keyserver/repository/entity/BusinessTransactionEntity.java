/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// JPA entity for the `business_transactions` table.
///
/// ## ID strategy
///
/// The primary key is a **TSID** (Time-Sorted Unique Identifier) stored as a
/// `BIGINT`.  The TSID is assigned via `@PrePersist` using a node-aware
/// `TsidFactory` (injected upstream and passed to the static factory method
/// `started(…)`), not `TSID.fast()`, so that multiple server instances
/// (each with a distinct `TSID_NODE` env-var value) never collide.
///
/// ## Transaction discipline
///
/// All writes to this table use `@Transactional(REQUIRES_NEW)` so that the
/// BTX record is committed immediately and remains visible even if the
/// command's own JTA transaction rolls back.
@Entity
@Table(name = "business_transactions")
public class BusinessTransactionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private long id;

    @Column(name = "command_type", nullable = false, updatable = false)
    private String commandType;

    @Column(name = "fingerprint")
    private @Nullable String fingerprint;

    @Column(name = "caller_ip")
    private @Nullable String callerIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private BusinessTransactionState state;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private @Nullable OffsetDateTime completedAt;

    @Column(name = "error_type")
    private @Nullable String errorType;

    @Column(name = "error_message")
    private @Nullable String errorMessage;

    protected BusinessTransactionEntity() {}

    @PrePersist
    protected void prePersist() {
        if (this.startedAt == null) {
            this.startedAt = OffsetDateTime.now();
        }
    }

    /// Creates a new entity in `STARTED` state with the given TSID.
    ///
    /// Callers are responsible for supplying a TSID from the shared node-aware
    /// `TsidFactory` — never from `TSID.fast()` — to guarantee uniqueness
    /// across all server instances.
    public static BusinessTransactionEntity started(long tsid, String commandType, @Nullable String callerIp) {
        var entity = new BusinessTransactionEntity();
        entity.id = tsid;
        entity.commandType = commandType;
        entity.callerIp = callerIp;
        entity.state = BusinessTransactionState.STARTED;
        return entity;
    }

    public void markFingerprintSet(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public void markCompleted() {
        this.state = BusinessTransactionState.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed(String errorType, @Nullable String errorMessage) {
        this.state = BusinessTransactionState.FAILED;
        this.completedAt = OffsetDateTime.now();
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public long getId() {
        return id;
    }

    public String getCommandType() {
        return commandType;
    }

    public Optional<String> getFingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public Optional<String> getCallerIp() {
        return Optional.ofNullable(callerIp);
    }

    public BusinessTransactionState getState() {
        return state;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public Optional<OffsetDateTime> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<String> getErrorType() {
        return Optional.ofNullable(errorType);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
