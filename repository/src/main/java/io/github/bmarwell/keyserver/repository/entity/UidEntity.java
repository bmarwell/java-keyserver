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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Optional;

/// JPA entity for the `uids` table. One row per OpenPGP User ID bound to a key.
/// Not a record: JPA requires a no-arg constructor and mutable state for proxy support.
@Entity
@Table(name = "uids")
public class UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fingerprint", nullable = false, updatable = false)
    private KeyEntity key;

    /// Raw UID string as it appears in the key packet, e.g. `Alice <alice@example.org>`.
    @Column(name = "uid_raw", nullable = false, updatable = false)
    private String uidRaw;

    @Column(name = "uid_name")
    private String uidName;

    /// Parsed email component; absent for UIDs without an RFC 2822 address.
    @Column(name = "uid_email")
    private String uidEmail;

    @Column(name = "creation_time")
    private OffsetDateTime creationTime;

    @Column(name = "expiration_time")
    private OffsetDateTime expirationTime;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    protected UidEntity() {}

    public UidEntity(KeyEntity key, String uidRaw) {
        this.key = key;
        this.uidRaw = uidRaw;
    }

    public Long getId() {
        return id;
    }

    public KeyEntity getKey() {
        return key;
    }

    void setKey(KeyEntity key) {
        this.key = key;
    }

    public String getUidRaw() {
        return uidRaw;
    }

    public Optional<String> getUidName() {
        return Optional.ofNullable(uidName);
    }

    public void setUidName(String uidName) {
        this.uidName = uidName;
    }

    public Optional<String> getUidEmail() {
        return Optional.ofNullable(uidEmail);
    }

    public void setUidEmail(String uidEmail) {
        this.uidEmail = uidEmail;
    }

    public Optional<OffsetDateTime> getCreationTime() {
        return Optional.ofNullable(creationTime);
    }

    public void setCreationTime(OffsetDateTime creationTime) {
        this.creationTime = creationTime;
    }

    public Optional<OffsetDateTime> getExpirationTime() {
        return Optional.ofNullable(expirationTime);
    }

    public void setExpirationTime(OffsetDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
