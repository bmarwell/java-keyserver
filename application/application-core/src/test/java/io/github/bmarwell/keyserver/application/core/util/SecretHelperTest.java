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
package io.github.bmarwell.keyserver.application.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class SecretHelperTest {

    final SecretHelper secretHelper = new SecretHelper();

    @RepeatedTest(20)
    void secretHelper_length_is_always_32() {
        // when
        final var newSecret = secretHelper.createNewSecret();

        // then
        assertThat(newSecret).hasSize(32);
    }

    @RepeatedTest(20)
    void secretHelper_does_not_generate_special_chars() {
        // when
        final var newSecret = secretHelper.createNewSecret();

        // then
        assertThat(newSecret).doesNotContain("=", "+", "\\", "/");
    }
}
