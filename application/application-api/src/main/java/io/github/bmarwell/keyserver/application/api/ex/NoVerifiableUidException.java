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

/// Thrown when every UID on the submitted key either has no email address,
/// is revoked, or is expired — leaving nothing that can be verified.
public final class NoVerifiableUidException extends KeyValidationException {

    public NoVerifiableUidException(String message, KeyFingerprint fingerprint) {
        super(message, Optional.of(fingerprint));
    }

    /// Used when the fingerprint was not extractable before UID scanning.
    public NoVerifiableUidException(String message) {
        super(message, Optional.empty());
    }
}
