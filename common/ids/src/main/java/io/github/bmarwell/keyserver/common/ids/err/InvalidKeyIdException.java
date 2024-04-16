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
package io.github.bmarwell.keyserver.common.ids.err;

import java.util.StringJoiner;

public class InvalidKeyIdException extends RuntimeException {

    private final String keyId;

    public InvalidKeyIdException(String message, String keyId) {
        super(message);
        this.keyId = keyId;
    }

    public String getKeyId() {
        return keyId;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", InvalidKeyIdException.class.getSimpleName() + "[", "]")
                .add("super=" + super.toString())
                .add("keyId='" + keyId + "'")
                .toString();
    }
}