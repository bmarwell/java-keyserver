/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// JPA entity for the `audit_log` table.
/// Every command dispatch writes one row regardless of outcome.
///
/// A plain `BIGSERIAL` / `IDENTITY` is used here rather than TSID because audit
/// rows are never referenced externally and carry no security-sensitive token.
/// Sequential DB-assigned integers are the simplest correct choice for a
/// write-only append log.
///
/// Each row references the `business_transactions` row via `btxId`, so admins
/// can join audit entries with the full BTX lifecycle record.
///
/// Not a record: JPA requires a no-arg constructor and mutable state for proxy support.
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /// Foreign key to `business_transactions.id`.  Every audit entry belongs to exactly one BTX.
    @Column(name = "btx_id", nullable = false, updatable = false)
    private long btxId;

    @Column(name = "command_type", nullable = false, updatable = false)
    private String commandType;

    /// Absent when the fingerprint could not be extracted from malformed input.
    @Column(name = "fingerprint", updatable = false)
    private @Nullable String fingerprint;

    @Column(name = "request_ip", updatable = false)
    private @Nullable String requestIp;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "result", nullable = false, updatable = false)
    private String result;

    @Column(name = "failure_type", updatable = false)
    private @Nullable String failureType;

    @Column(name = "failure_message", updatable = false)
    private @Nullable String failureMessage;

    protected AuditLogEntity() {}

    private AuditLogEntity(long btxId, String commandType, @Nullable String requestIp, @Nullable String fingerprint) {
        this.btxId = btxId;
        this.commandType = commandType;
        this.requestIp = requestIp;
        this.fingerprint = fingerprint;
        this.occurredAt = OffsetDateTime.now();
    }

    public static AuditLogEntity success(
            long btxId, String commandType, @Nullable String requestIp, @Nullable String fingerprint) {
        var entry = new AuditLogEntity(btxId, commandType, requestIp, fingerprint);
        entry.result = "SUCCESS";
        return entry;
    }

    public static AuditLogEntity failure(
            long btxId,
            String commandType,
            @Nullable String requestIp,
            @Nullable String fingerprint,
            String failureType,
            @Nullable String failureMessage) {
        var entry = new AuditLogEntity(btxId, commandType, requestIp, fingerprint);
        entry.result = "FAILURE";
        entry.failureType = failureType;
        entry.failureMessage = failureMessage;
        return entry;
    }

    public Long getId() {
        return id;
    }

    public long getBtxId() {
        return btxId;
    }

    public String getCommandType() {
        return commandType;
    }

    public Optional<String> getFingerprint() {
        return Optional.ofNullable(fingerprint);
    }

    public Optional<String> getRequestIp() {
        return Optional.ofNullable(requestIp);
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getResult() {
        return result;
    }

    public Optional<String> getFailureType() {
        return Optional.ofNullable(failureType);
    }

    public Optional<String> getFailureMessage() {
        return Optional.ofNullable(failureMessage);
    }
}
