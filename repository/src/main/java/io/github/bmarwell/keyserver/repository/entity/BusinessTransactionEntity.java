/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private String fingerprint;

    @Column(name = "caller_ip")
    private String callerIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private BusinessTransactionState state;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

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
    public static BusinessTransactionEntity started(long tsid, String commandType, String callerIp) {
        var entity = new BusinessTransactionEntity();
        entity.id = tsid;
        entity.commandType = commandType;
        entity.callerIp = callerIp;
        entity.state = BusinessTransactionState.STARTED;
        return entity;
    }

    public void markCompleted() {
        this.state = BusinessTransactionState.COMPLETED;
        this.completedAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.state = BusinessTransactionState.FAILED;
        this.completedAt = OffsetDateTime.now();
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

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
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
}
