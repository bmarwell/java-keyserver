/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.repository.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// JPA entity for the `keys` table.
/// Not a record: JPA requires a no-arg constructor and mutable state for proxy support.
@Entity
@Table(name = "keys")
public class KeyEntity {

    /// Full hex fingerprint: 40 chars for v4 keys, 64 chars for v5 keys.
    /// This is the natural primary key — it is stable, globally unique, and
    /// already the canonical identity of an OpenPGP key.
    @Id
    @Column(name = "fingerprint", nullable = false, updatable = false)
    private String fingerprint;

    /// Reversed fingerprint, computed by the DB (`GENERATED ALWAYS AS`).
    /// Enables efficient suffix scans (e.g. short/long key-ID prefix search on the reversed value).
    @Column(name = "rfingerprint", nullable = false, insertable = false, updatable = false)
    private String rfingerprint;

    /// Last 16 hex chars of the fingerprint, computed by the DB (`GENERATED ALWAYS AS`).
    /// Covers both short (8-char) and long (16-char) key-ID lookups without a separate column.
    @Column(name = "keyid_long", nullable = false, insertable = false, updatable = false)
    private String keyidLong;

    @Column(name = "version", nullable = false)
    private int version;

    /// OpenPGP algorithm tag per RFC 4880 / RFC 9580.
    @Column(name = "algorithm", nullable = false)
    private int algorithm;

    /// Null for fixed-length ECC algorithms such as Ed25519 or X25519.
    @Column(name = "bit_strength")
    private Integer bitStrength;

    @Column(name = "creation_time", nullable = false)
    private OffsetDateTime creationTime;

    @Column(name = "expiration_time")
    private OffsetDateTime expirationTime;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    /// Keyserver-administrative flag: true if a keyserver operator has explicitly
    /// disabled this key.  Unlike {@code revoked}, this flag is not set by the key
    /// owner and is not reflected in the OpenPGP key material.
    @Column(name = "disabled", nullable = false)
    private boolean disabled;

    /// Full ASCII-armored public key block returned verbatim on `op=get`.
    @Column(name = "armored_key", nullable = false)
    private String armoredKey;

    @Column(name = "md5", nullable = false)
    private String md5;

    @Column(name = "ctime", nullable = false)
    private OffsetDateTime ctime;

    @Column(name = "mtime", nullable = false)
    private OffsetDateTime mtime;

    @OneToMany(mappedBy = "key", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UidEntity> uids = new ArrayList<>();

    protected KeyEntity() {}

    public KeyEntity(
            String fingerprint,
            int version,
            int algorithm,
            OffsetDateTime creationTime,
            String armoredKey,
            String md5) {
        this.fingerprint = fingerprint;
        this.version = version;
        this.algorithm = algorithm;
        this.creationTime = creationTime;
        this.armoredKey = armoredKey;
        this.md5 = md5;
        this.ctime = OffsetDateTime.now();
        this.mtime = OffsetDateTime.now();
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getRfingerprint() {
        return rfingerprint;
    }

    public String getKeyidLong() {
        return keyidLong;
    }

    public int getVersion() {
        return version;
    }

    public int getAlgorithm() {
        return algorithm;
    }

    public Optional<Integer> getBitStrength() {
        return Optional.ofNullable(bitStrength);
    }

    public void setBitStrength(Integer bitStrength) {
        this.bitStrength = bitStrength;
    }

    public OffsetDateTime getCreationTime() {
        return creationTime;
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
        this.mtime = OffsetDateTime.now();
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        this.mtime = OffsetDateTime.now();
    }

    public String getArmoredKey() {
        return armoredKey;
    }

    public void setArmoredKey(String armoredKey) {
        this.armoredKey = armoredKey;
    }

    public void setMtime(OffsetDateTime mtime) {
        this.mtime = mtime;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public OffsetDateTime getCtime() {
        return ctime;
    }

    public OffsetDateTime getMtime() {
        return mtime;
    }

    public List<UidEntity> getUids() {
        return List.copyOf(uids);
    }

    public void addUid(UidEntity uid) {
        uids.add(uid);
        uid.setKey(this);
    }
}
