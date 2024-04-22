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

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Locale;

public record ReversedKeyFingerprint(String value) {

    public ReversedKeyFingerprint(String value) {
        this.value = value.toLowerCase(Locale.ROOT);
    }

    public static ReversedKeyFingerprint fromFingerprint(KeyFingerprint keyFingerprint) {
        final var fp = keyFingerprint.value();
        final var rfpBuilder = new StringBuilder(fp.length());
        rfpBuilder.append(keyFingerprint.value());
        rfpBuilder.reverse();

        return new ReversedKeyFingerprint(rfpBuilder.toString());
    }
}
