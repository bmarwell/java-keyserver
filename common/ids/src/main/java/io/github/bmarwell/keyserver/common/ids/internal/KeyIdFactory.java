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
package io.github.bmarwell.keyserver.common.ids.internal;

import io.github.bmarwell.keyserver.common.ids.KeyId;
import io.github.bmarwell.keyserver.common.ids.err.InvalidKeyIdException;
import java.util.Locale;

public final class KeyIdFactory {

    private KeyIdFactory() {
        // util
    }

    public static KeyId fromString(String keyId) {
        // Key ID strings may be
        // 8 digits (32-bit key ID),
        // 16 digits (64-bit key ID),
        // 32 digits (version 3 fingerprint), or
        // 40 digits (version 4 fingerprint).
        // The hexadecimal digits are not case-sensitive.
        //
        // A keyserver that allows searching by keyid MUST accept the 160-bit version 4 fingerprint,
        // 64-bit key IDs, and 32-bit key IDs in the "search" variable.
        if (keyId.length() < 8) {
            throw new InvalidKeyIdException("Key string too short! ", keyId);
        }

        if (keyId.startsWith("0x") && keyId.length() < 10) {
            throw new InvalidKeyIdException("Key string too short!", keyId);
        }

        var trimmedLower = getStrip(keyId);

        return new KeyIdImplementation(trimmedLower);
    }

    private static String getStrip(String keyId) {
        if (keyId.startsWith("0x")) {
            return keyId.substring(2).toLowerCase(Locale.ROOT).strip();
        }

        return keyId.toLowerCase(Locale.ROOT).strip();
    }
}
