/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
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
