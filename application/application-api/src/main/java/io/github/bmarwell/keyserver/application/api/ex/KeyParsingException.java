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

import java.util.Optional;

/// Thrown when the submitted key material cannot be decoded at all —
/// e.g. the ASCII armor is corrupted or the byte stream is not a valid OpenPGP structure.
/// No fingerprint is available because parsing failed before one could be extracted.
public final class KeyParsingException extends KeyServerException {

    public KeyParsingException(String message, Throwable cause) {
        super(message, cause, Optional.empty());
    }

    public KeyParsingException(String message) {
        super(message, Optional.empty());
    }
}
