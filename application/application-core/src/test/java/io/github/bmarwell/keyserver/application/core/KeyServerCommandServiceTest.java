/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.application.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bmarwell.keyserver.application.api.commands.CommandCallerContext;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommand;
import io.github.bmarwell.keyserver.application.api.commands.KeyServerCommandResponse;
import io.github.bmarwell.keyserver.application.core.cmdhandler.CommandHandler;
import io.github.bmarwell.keyserver.application.core.concurrent.BusinessTransactionContext;
import io.github.bmarwell.keyserver.application.port.repository.BusinessTransactionRepository;
import io.github.bmarwell.keyserver.test.utils.cdi.SimpleInstance;
import io.hypersistence.tsid.TSID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KeyServerCommandServiceTest {

    private record TestCommand() implements KeyServerCommand {}

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
        // given
        // No handler is registered, so the dispatcher must fail and the BTX must end up FAILED.
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when
        // @Asynchronous is not intercepted in unit tests, so the service runs synchronously here.
        service.handleCommand(noopCommand, CommandCallerContext.empty());

        // then
        assertThat(trackingRepo.startedCount)
                .as("the BTX row must be opened before dispatch so failed commands are still auditable")
                .isEqualTo(1);
        assertThat(trackingRepo.failedCount)
                .as("unknown commands must mark the BTX as FAILED instead of leaving it in STARTED")
                .isEqualTo(1);
        assertThat(trackingRepo.completedCount)
                .as("a failed dispatch must never mark the BTX as COMPLETED")
                .isZero();
        assertThat(trackingRepo.lastErrorType)
                .as("the BTX failure audit must retain the concrete exception type for diagnosis")
                .isEqualTo("UnsupportedOperationException");
    }

    @Test
    void forwards_callerIp_from_context_to_recordStarted() {
        // given
        // The anonymized caller IP is part of the BTX audit trail and must survive dispatch unchanged.
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when
        service.handleCommand(noopCommand, CommandCallerContext.of("192.168.1.0"));

        // then
        assertThat(trackingRepo.lastCallerIp)
                .as("the anonymized IP must reach recordStarted unchanged so the BTX audit row keeps caller context")
                .isEqualTo("192.168.1.0");
    }

    @Test
    void records_null_callerIp_when_context_is_empty() {
        // given
        // Internally triggered commands may not have caller metadata, and that absence must persist cleanly.
        var noopCommand = new KeyServerCommand() {};
        var trackingRepo = new TrackingBusinessTransactionRepository();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.empty());

        // when
        service.handleCommand(noopCommand, CommandCallerContext.empty());

        // then
        assertThat(trackingRepo.lastCallerIp)
                .as("empty caller context must not invent an IP address in the BTX audit row")
                .isNull();
    }

    @Test
    void ignores_record_completed_failure_after_successful_dispatch() {
        // given
        // Issue #134: a successful handler plus failing recordCompleted() must not reclassify the BTX as FAILED.
        var trackingRepo = new TrackingBusinessTransactionRepository();
        trackingRepo.throwOnRecordCompleted = true;
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(handler.executeCount)
                .as("the handler must still execute successfully before the completion write fails")
                .isEqualTo(1);
        assertThat(trackingRepo.startedCount)
                .as("the BTX row must still be opened for a successful command")
                .isEqualTo(1);
        assertThat(trackingRepo.recordCompletedAttemptCount)
                .as("the regression test must prove the service reached recordCompleted() before the simulated failure")
                .isEqualTo(1);
        assertThat(trackingRepo.completedCount)
                .as("the repository never recorded COMPLETED because recordCompleted() threw")
                .isZero();
        assertThat(trackingRepo.failedCount)
                .as("a completion-write failure after a successful handler must not mark the BTX as FAILED")
                .isZero();
    }

    private static final class SuccessCommandHandler implements CommandHandler<TestCommand> {
        int executeCount;

        @Override
        public <C extends KeyServerCommand> boolean canHandle(C command) {
            return command instanceof TestCommand;
        }

        @Override
        public KeyServerCommandResponse execute(KeyServerCommand command, CommandCallerContext callerContext) {
            this.executeCount++;
            return KeyServerCommandResponse.success();
        }
    }

    private static final class TrackingBusinessTransactionRepository implements BusinessTransactionRepository {
        int startedCount;
        int recordCompletedAttemptCount;
        int completedCount;
        int failedCount;
        boolean throwOnRecordCompleted;

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
            this.recordCompletedAttemptCount++;
            if (this.throwOnRecordCompleted) {
                throw new IllegalStateException("simulated completion write failure");
            }
            this.completedCount++;
        }

        @Override
        public void recordFailed(long btxId, String errorType, @Nullable String errorMessage) {
            failedCount++;
            lastErrorType = errorType;
        }
    }
}
