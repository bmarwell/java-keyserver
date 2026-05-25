/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.core.cmdhandler.CommandHandler;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.test.utils.cdi.SimpleInstance;
import io.hypersistence.tsid.TSID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KeyServerCommandServiceTest {

    private static KeyServerCommandService buildService(
            TrackingBusinessTransactionRepository trackingRepo,
            SimpleInstance<CommandHandler<? extends KeyServerCommand>> handlers) {
        var dispatcher = new TransactionalCommandDispatcher();
        dispatcher.setCommandHandlers(handlers);
        KeyServerCommandService service = new KeyServerCommandService();
        service.setDispatcher(dispatcher);
        service.setBtxRepository(trackingRepo);
        service.setBtxContext(new BusinessTransactionContext());
        service.setTsidFactory(
                TSID.Factory.builder().withNodeBits(10).withNode(0).build());
        return service;
    }

    @Test
    void records_failed_on_unknown_command_type() {
        // given:
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when: @Asynchronous is not intercepted in unit tests — runs synchronously
        service.handleCommand(noopCommand, CommandCallerContext.empty());

        // then: BTX was started and then recorded as failed with the exception type
        assertThat(trackingRepo.startedCount).isEqualTo(1);
        assertThat(trackingRepo.failedCount).isEqualTo(1);
        assertThat(trackingRepo.completedCount).isZero();
        assertThat(trackingRepo.lastErrorType).isEqualTo("UnsupportedOperationException");
    }

    @Test
    void forwards_callerIp_from_context_to_recordStarted() {
        // given:
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when:
        service.handleCommand(noopCommand, CommandCallerContext.of("192.168.1.0"));

        // then: the anonymized IP must reach recordStarted unchanged
        assertThat(trackingRepo.lastCallerIp).isEqualTo("192.168.1.0");
    }

    @Test
    void records_null_callerIp_when_context_is_empty() {
        // given:
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when:
        service.handleCommand(noopCommand, CommandCallerContext.empty());

        // then:
        assertThat(trackingRepo.lastCallerIp).isNull();
    }

    private static final class TrackingBusinessTransactionRepository implements BusinessTransactionRepository {
        int startedCount;
        int completedCount;
        int failedCount;

        @Nullable
        String lastCallerIp;

        @Nullable
        String lastErrorType;

        @Override
        public void recordStarted(long btxId, String commandType, @Nullable String callerIp) {
            startedCount++;
            lastCallerIp = callerIp;
        }

        @Override
        public void recordCompleted(long btxId) {
            completedCount++;
        }

        @Override
        public void recordFailed(long btxId, String errorType, @Nullable String errorMessage) {
            failedCount++;
            lastErrorType = errorType;
        }
    }
}
