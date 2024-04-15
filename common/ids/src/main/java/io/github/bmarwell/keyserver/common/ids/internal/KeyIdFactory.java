/*
 * Copyright (C) 2023-2024 The SIPper project team.
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
import java.util.Locale;

public final class KeyIdFactory {

    private KeyIdFactory() {
        // util
    }

    public static KeyId fromString(String keyId) {
        if (keyId.length() < 16) {
            throw new IllegalArgumentException("Key string too short! " + keyId);
        }

        if (keyId.startsWith("0x") && keyId.length() < 18) {
            throw new IllegalArgumentException("Key string too short!" + keyId);
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
