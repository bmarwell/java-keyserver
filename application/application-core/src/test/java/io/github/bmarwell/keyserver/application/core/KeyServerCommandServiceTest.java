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
        return buildService(trackingRepo, handlers, new BusinessTransactionContext());
    }

    private static KeyServerCommandService buildService(
            TrackingBusinessTransactionRepository trackingRepo,
            SimpleInstance<CommandHandler<? extends KeyServerCommand>> handlers,
            BusinessTransactionContext btxContext) {
        var dispatcher = new TransactionalCommandDispatcher();
        dispatcher.setCommandHandlers(handlers);
        KeyServerCommandService service = new KeyServerCommandService();
        service.setDispatcher(dispatcher);
        service.setBtxRepository(trackingRepo);
        service.setBtxContext(btxContext);
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
    void swallows_record_started_failure_before_dispatch() {
        // given
        // Fire-and-forget dispatch must not propagate when the STARTED write itself fails.
        var trackingRepo = new ThrowingOnRecordStartedRepository();
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(trackingRepo.recordStartedAttemptCount)
                .as("the service must attempt to create the STARTED BTX row before any handler dispatch")
                .isEqualTo(1);
        assertThat(handler.executeCount)
                .as("dispatch must not continue when the STARTED BTX write failed")
                .isZero();
        assertThat(trackingRepo.failedCount)
                .as("without a durable STARTED row, the service cannot persist a terminal BTX failure")
                .isZero();
        assertThat(trackingRepo.completedCount)
                .as("a command that never dispatched must not reach COMPLETED")
                .isZero();
    }

    @Test
    void records_failed_when_context_initialize_throws_after_record_started() {
        // given
        // Once the STARTED row exists, a later context-initialization failure must still end in a terminal BTX state.
        var trackingRepo = new TrackingBusinessTransactionRepository();
        var handler = new SuccessCommandHandler();
        var btxContext = new BusinessTransactionContext();
        btxContext.initialize(123L);
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler), btxContext);

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(trackingRepo.startedCount)
                .as("the BTX row must already exist before the second initialize() call fails")
                .isEqualTo(1);
        assertThat(handler.executeCount)
                .as("dispatch must not continue after the request-scoped BTX context rejects reinitialization")
                .isZero();
        assertThat(trackingRepo.failedCount)
                .as("an initialization failure after recordStarted() must still move the BTX to FAILED")
                .isEqualTo(1);
        assertThat(trackingRepo.completedCount)
                .as("a command that never dispatched must not reach COMPLETED")
                .isZero();
        assertThat(trackingRepo.lastErrorType)
                .as("the persisted BTX failure should keep the concrete initialization exception type")
                .isEqualTo("IllegalStateException");
        assertThat(trackingRepo.lastErrorMessage)
                .as("the BTX audit message should preserve why the request-scoped context rejected initialization")
                .isEqualTo("BusinessTransactionContext already initialized");
    }

    @Test
    void records_completed_after_successful_dispatch() {
        // given
        // A successful handler must drive the normal BTX happy path all the way to recordCompleted().
        var trackingRepo = new TrackingBusinessTransactionRepository();
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(handler.executeCount)
                .as("the happy path must still execute the matched command handler exactly once")
                .isEqualTo(1);
        assertThat(trackingRepo.startedCount)
                .as("successful commands must still open a BTX row before dispatch")
                .isEqualTo(1);
        assertThat(trackingRepo.recordCompletedAttemptCount)
                .as("the success path must reach the shared recordCompleted() implementation")
                .isEqualTo(1);
        assertThat(trackingRepo.completedCount)
                .as("successful commands must mark the BTX as COMPLETED")
                .isEqualTo(1);
        assertThat(trackingRepo.failedCount)
                .as("the happy path must not record a BTX failure")
                .isZero();
    }

    @Test
    void records_completed_when_first_completion_write_fails_but_retry_succeeds() {
        // given
        // Issue #149: a transient completion-write failure must not permanently mark the BTX FAILED when a retry
        // succeeds; the BTX must end up COMPLETED as if nothing went wrong.
        var trackingRepo = new FailOnceOnRecordCompletedRepository();
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(trackingRepo.recordCompletedAttemptCount)
                .as("the service must attempt recordCompleted() twice: once failing, once succeeding")
                .isEqualTo(2);
        assertThat(trackingRepo.completedCount)
                .as("the retry must land the BTX in COMPLETED rather than FAILED")
                .isEqualTo(1);
        assertThat(trackingRepo.failedCount)
                .as("a transient first failure followed by a successful retry must not record any FAILED state")
                .isZero();
    }

    @Test
    void records_failed_when_all_completion_write_attempts_fail() {
        // given
        // Only when both the initial attempt and the retry both fail should the BTX fall back to FAILED.
        var trackingRepo = new ThrowingOnRecordCompletedRepository();
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(handler.executeCount)
                .as("the handler must still execute successfully before the completion writes fail")
                .isEqualTo(1);
        assertThat(trackingRepo.startedCount)
                .as("the BTX row must still be opened for a successful command")
                .isEqualTo(1);
        assertThat(trackingRepo.recordCompletedAttemptCount)
                .as("the service must attempt recordCompleted() twice (initial + retry) before giving up")
                .isEqualTo(2);
        assertThat(trackingRepo.completedCount)
                .as("the repository must not report COMPLETED when both completion writes failed")
                .isZero();
        assertThat(trackingRepo.failedCount)
                .as("exhausting all retry attempts must still move the BTX to a terminal FAILED state")
                .isEqualTo(1);
        assertThat(trackingRepo.lastErrorType)
                .as("the retry exception type must be stored so operators can diagnose persistent completion failures")
                .isEqualTo("IllegalStateException");
        assertThat(trackingRepo.lastErrorMessage)
                .as("the retry exception message must be stored unchanged without added prefixes")
                .isEqualTo("simulated completion write failure");
    }

    @Test
    void swallows_record_failed_when_all_completion_and_fallback_writes_throw() {
        // given
        // Even if every write attempt fails, the async service must log and swallow rather than propagate.
        var trackingRepo = new ThrowingOnCompletionAndFailureRepository();
        var handler = new SuccessCommandHandler();
        KeyServerCommandService service = buildService(trackingRepo, SimpleInstance.of(handler));

        // when
        service.handleCommand(new TestCommand(), CommandCallerContext.empty());

        // then
        assertThat(handler.executeCount)
                .as("the command handler must already have completed before the BTX completion writes fail")
                .isEqualTo(1);
        assertThat(trackingRepo.recordCompletedAttemptCount)
                .as("the service must attempt recordCompleted() twice (initial + retry) before falling back")
                .isEqualTo(2);
        assertThat(trackingRepo.recordFailedAttemptCount)
                .as("after both completion attempts fail, the service must attempt the terminal FAILED fallback")
                .isEqualTo(1);
        assertThat(trackingRepo.failedCount)
                .as("the fallback write never persisted because the repository simulated a second failure")
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

    private static class TrackingBusinessTransactionRepository implements BusinessTransactionRepository {
        int startedCount;
        int recordStartedAttemptCount;
        int recordCompletedAttemptCount;
        int recordFailedAttemptCount;
        int completedCount;
        int failedCount;

        @Nullable
        String lastCallerIp;

        @Nullable
        String lastErrorType;

        @Nullable
        String lastErrorMessage;

        @Override
        public void recordStarted(long btxId, String commandType, @Nullable String callerIp) {
            this.recordStartedAttemptCount++;
            this.beforeMarkingStarted(btxId, commandType, callerIp);
            this.startedCount++;
            this.lastCallerIp = callerIp;
        }

        @Override
        public void recordCompleted(long btxId) {
            this.recordCompletedAttemptCount++;
            this.beforeMarkingCompleted(btxId);
            this.completedCount++;
        }

        @Override
        public void recordFailed(long btxId, String errorType, @Nullable String errorMessage) {
            this.recordFailedAttemptCount++;
            this.beforeMarkingFailed(btxId, errorType, errorMessage);
            this.failedCount++;
            this.lastErrorType = errorType;
            this.lastErrorMessage = errorMessage;
        }

        protected void beforeMarkingStarted(long btxId, String commandType, @Nullable String callerIp) {}

        protected void beforeMarkingCompleted(long btxId) {}

        protected void beforeMarkingFailed(long btxId, String errorType, @Nullable String errorMessage) {}
    }

    private static final class ThrowingOnRecordStartedRepository extends TrackingBusinessTransactionRepository {
        @Override
        protected void beforeMarkingStarted(long btxId, String commandType, @Nullable String callerIp) {
            throw new IllegalStateException("simulated started write failure");
        }
    }

    private static class ThrowingOnRecordCompletedRepository extends TrackingBusinessTransactionRepository {
        @Override
        protected void beforeMarkingCompleted(long btxId) {
            throw new IllegalStateException("simulated completion write failure");
        }
    }

    private static final class FailOnceOnRecordCompletedRepository extends TrackingBusinessTransactionRepository {
        @Override
        protected void beforeMarkingCompleted(long btxId) {
            // Throw only on the first attempt to simulate a transient failure that the retry resolves.
            if (this.recordCompletedAttemptCount == 1) {
                throw new IllegalStateException("simulated transient completion write failure");
            }
        }
    }

    private static final class ThrowingOnCompletionAndFailureRepository extends ThrowingOnRecordCompletedRepository {
        @Override
        protected void beforeMarkingFailed(long btxId, String errorType, @Nullable String errorMessage) {
            throw new IllegalStateException("simulated fallback write failure");
        }
    }
}
