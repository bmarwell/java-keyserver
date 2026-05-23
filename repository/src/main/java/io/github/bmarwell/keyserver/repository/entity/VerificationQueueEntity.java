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

import io.hypersistence.tsid.TSID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/// JPA entity for the `verification_queue` table.
///
/// ## Why not use the key fingerprint as the primary key here?
///
/// A single key can carry multiple UIDs.  Each UID that has an email address
/// gets its own queue entry and its own verification email, so the fingerprint
/// alone is not unique within this table.  The same key can also be re-submitted
/// after a previous token expires, producing another row.
/// Additionally, this `id` is embedded verbatim in the verification link sent to
/// the user.  Using the fingerprint as the token would leak which key is under
/// verification to anyone who intercepts the URL; a surrogate keeps that
/// information out of the token.
///
/// ## Why TSID stored as `BIGINT`?
///
/// TSID encodes a millisecond timestamp, a node ID, and a sequence counter into
/// 64 bits, so it fits in a `BIGINT` (8 bytes) rather than a UUID's 16 bytes.
/// Because the timestamp is in the high bits, sequential inserts remain
/// time-sorted and the B-tree index on `id` stays clustered — avoiding the
/// random page-split fragmentation that UUID v4 causes.
/// As a verification token the value is still globally unguessable: the
/// nanosecond-resolution timestamp component combined with the random node bits
/// and sequence makes enumeration infeasible.
///
/// The value is assigned via {@link #assignId()} in a `@PrePersist` callback so
/// that no DB sequence, trigger, or `DEFAULT` expression is needed.
///
/// Not a record: JPA requires a no-arg constructor and mutable state for proxy support.
@Entity
@Table(name = "verification_queue")
public class VerificationQueueEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "fingerprint", nullable = false, updatable = false)
    private String fingerprint;

    @Column(name = "uid_raw", nullable = false, updatable = false)
    private String uidRaw;

    @Column(name = "uid_email", nullable = false, updatable = false)
    private String uidEmail;

    @Column(name = "armored_key", nullable = false, updatable = false)
    private String armoredKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private VerificationQueueState state = VerificationQueueState.PENDING;

    protected VerificationQueueEntity() {}

    public VerificationQueueEntity(
            long id, String fingerprint, String uidRaw, String uidEmail, String armoredKey, OffsetDateTime expiresAt) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.uidRaw = uidRaw;
        this.uidEmail = uidEmail;
        this.armoredKey = armoredKey;
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    @PrePersist
    private void assignId() {
        if (this.id == null) {
            this.id = TSID.fast().toLong();
        }
    }

    public Long getId() {
        return id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getUidRaw() {
        return uidRaw;
    }

    public String getUidEmail() {
        return uidEmail;
    }

    public String getArmoredKey() {
        return armoredKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public VerificationQueueState getState() {
        return state;
    }

    public void setState(VerificationQueueState state) {
        this.state = state;
    }
}
