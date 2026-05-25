/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.test.utils.cdi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimpleInstanceTest {

    @Test
    void returns_empty_stream_on_empty() {
        // given
        SimpleInstance<Object> empty = SimpleInstance.empty();

        // expect
        assertThat(empty.stream()).isEmpty();
    }
}
