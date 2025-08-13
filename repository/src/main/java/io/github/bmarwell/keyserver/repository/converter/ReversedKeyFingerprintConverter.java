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
package io.github.bmarwell.keyserver.repository.converter;

import io.github.bmarwell.keyserver.repository.pdo.ReversedKeyFingerprint;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ReversedKeyFingerprintConverter implements AttributeConverter<ReversedKeyFingerprint, String> {

    @Override
    public String convertToDatabaseColumn(ReversedKeyFingerprint attribute) {
        return attribute.value();
    }

    @Override
    public ReversedKeyFingerprint convertToEntityAttribute(String dbData) {
        return new ReversedKeyFingerprint(dbData);
    }
}
