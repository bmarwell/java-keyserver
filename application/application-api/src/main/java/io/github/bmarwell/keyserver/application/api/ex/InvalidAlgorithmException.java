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
package io.github.bmarwell.keyserver.application.api.ex;

import io.github.bmarwell.keyserver.common.ids.KeyFingerprint;
import java.util.Optional;

/// Thrown when the key uses a public-key algorithm that this server does not accept
/// (e.g. v3 RSA with MD5, DSA below 2048 bits).
public final class InvalidAlgorithmException extends KeyValidationException {

    /// The numeric OpenPGP algorithm tag that was rejected.
    private final int algorithmTag;

    public InvalidAlgorithmException(String message, int algorithmTag, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
        this.algorithmTag = algorithmTag;
    }

    public InvalidAlgorithmException(String message, int algorithmTag) {
        super(message, Optional.empty());
        this.algorithmTag = algorithmTag;
    }

    public int getAlgorithmTag() {
        return algorithmTag;
    }
}
