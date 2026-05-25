/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids;

import io.github.bmarwell.keyserver.common.ids.internal.KeyIdFactory;

/**
 * ID of an OpenPGP key. Can be a short or long key ID, where the long ID knows its short ID, but not vice versa.
 */
public interface KeyId {

    String value();

    default String valueWithHexPrefix() {
        return "0x" + value();
    }

    static KeyId fromString(String keyIdString) {
        return KeyIdFactory.fromString(keyIdString);
    }
}
