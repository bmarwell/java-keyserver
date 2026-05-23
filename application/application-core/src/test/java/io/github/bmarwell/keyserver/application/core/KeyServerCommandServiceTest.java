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
package io.github.bmarwell.keyserver.application.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.test.utils.cdi.SimpleInstance;
import io.hypersistence.tsid.TSID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class KeyServerCommandServiceTest {

    @Test
    void throws_on_unknown_command_type() {
        // given:
        var noopCommand = new KeyServerCommand() {};

        KeyServerCommandService service = new KeyServerCommandService();
        service.setCommandHandlers(SimpleInstance.empty());
        service.setBtxRepository(new NoopBusinessTransactionRepository());
        service.setBtxContext(new BusinessTransactionContext());
        service.setTsidFactory(
                TSID.Factory.builder().withNodeBits(10).withNode(0).build());

        // expect: the future completes exceptionally
        var future = service.handleCommand(noopCommand);
        assertThatThrownBy(future::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private static final class NoopBusinessTransactionRepository implements BusinessTransactionRepository {
        @Override
        public void recordStarted(long btxId, String commandType, String callerIp) {}

        @Override
        public void recordCompleted(long btxId) {}

        @Override
        public void recordFailed(long btxId) {}
    }
}
