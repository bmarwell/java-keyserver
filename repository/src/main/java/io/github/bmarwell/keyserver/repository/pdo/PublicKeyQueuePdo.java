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

import io.github.bmarwell.keyserver.repository.converter.ReversedKeyFingerprintConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "queued_keys")
public class PublicKeyQueuePdo {

    @Id
    @Convert(converter = ReversedKeyFingerprintConverter.class)
    @Column(name = "rfingerprint", columnDefinition = "text", length = 255, updatable = false)
    ReversedKeyFingerprint reversedKeyFingerprint;

    public PublicKeyQueuePdo() {}

    public PublicKeyQueuePdo(ReversedKeyFingerprint reversedKeyFingerprint) {
        this.reversedKeyFingerprint = reversedKeyFingerprint;
    }

    public ReversedKeyFingerprint getReversedKeyFingerprint() {
        return reversedKeyFingerprint;
    }

    public void setReversedKeyFingerprint(ReversedKeyFingerprint reversedKeyFingerprint) {
        this.reversedKeyFingerprint = reversedKeyFingerprint;
    }
}
