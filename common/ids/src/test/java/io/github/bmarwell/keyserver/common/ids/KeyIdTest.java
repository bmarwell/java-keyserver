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
