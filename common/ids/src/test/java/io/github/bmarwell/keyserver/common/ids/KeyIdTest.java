/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.common.ids;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class KeyIdTest {

    public static final String LONG_KEY_ID = "7BBF6E417BBF6E41";
    public static final String SHORT_KEY_ID = "7BBF6E417BBF6E41".substring(8);
    public static final String LONG_KEY_ID_LOWER = LONG_KEY_ID.toLowerCase(Locale.ROOT);

    @Test
    void can_identify_long_keyid() {
        // when:
        KeyId keyId = KeyId.fromString(LONG_KEY_ID);

        // then:
        assertThat(keyId.value()).isEqualTo(LONG_KEY_ID_LOWER);
    }

    @Test
    void can_identify_0xlong_keyid() {
        // given:
        String shortKeyIdString = "0x" + LONG_KEY_ID;

        // when:
        KeyId keyId = KeyId.fromString(shortKeyIdString);

        // then:
        assertThat(keyId.value()).isEqualTo(LONG_KEY_ID_LOWER);
    }

    @Test
    void can_identify_0xlong_keyid_lowercase() {
        // given:
        String shortKeyIdString = "0x" + LONG_KEY_ID_LOWER;

        // when:
        KeyId keyId = KeyId.fromString(shortKeyIdString);

        // then:
        assertThat(keyId.value()).isEqualTo(LONG_KEY_ID_LOWER);
    }
}
