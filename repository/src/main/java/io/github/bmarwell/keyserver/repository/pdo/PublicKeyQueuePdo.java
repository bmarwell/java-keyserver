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
package io.github.bmarwell.keyserver.repository.pdo;

import io.github.bmarwell.keyserver.repository.converter.InstantToTimestampUtcConverter;
import io.github.bmarwell.keyserver.repository.converter.ReversedKeyFingerprintConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Entity
@Table(name = "queued_keys")
public class PublicKeyQueuePdo {

    @Id
    @Convert(converter = ReversedKeyFingerprintConverter.class)
    @Column(name = "rfingerprint", columnDefinition = "text", length = 255, updatable = false)
    ReversedKeyFingerprint reversedKeyFingerprint;

    @Basic
    @Column(name = "itime")
    @Convert(converter = InstantToTimestampUtcConverter.class)
    Instant insertionTime;

    @Basic
    @Column(name = "secret")
    String secret;

    public PublicKeyQueuePdo() {}

    public PublicKeyQueuePdo(ReversedKeyFingerprint reversedKeyFingerprint, String secret) {
        this.reversedKeyFingerprint = reversedKeyFingerprint;
        this.insertionTime = Instant.now();
        this.secret = secret;
    }

    public ReversedKeyFingerprint getReversedKeyFingerprint() {
        return reversedKeyFingerprint;
    }

    public void setReversedKeyFingerprint(ReversedKeyFingerprint reversedKeyFingerprint) {
        this.reversedKeyFingerprint = reversedKeyFingerprint;
    }

    public Instant getInsertionTime() {
        return insertionTime;
    }

    public void setInsertionTime(Instant insertionTime) {
        this.insertionTime = insertionTime;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
