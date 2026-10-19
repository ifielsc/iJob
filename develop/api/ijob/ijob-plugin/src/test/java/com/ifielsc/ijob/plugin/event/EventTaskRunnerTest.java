package com.ifielsc.ijob.plugin.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.event.EventPublishCommand;
import com.ifielsc.ijob.core.event.EventReceiptResult;
import com.ifielsc.ijob.core.event.EventReceiptService;
import com.ifielsc.ijob.core.event.EventWaitRegistration;
import com.ifielsc.ijob.core.event.EventWaitRegistrationResult;
import com.ifielsc.ijob.core.event.EventWaitService;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClientException;
import com.ifielsc.ijob.core.resource.factory.file.client.FileInfo;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

class EventTaskRunnerTest {
    @Test
    void arbitraryFailuresNeverExposeSecretsAndKeepStableMetadata() {
        var service = new CapturingReceiptService();
        service.failure =
                new IllegalStateException(
                        "SELECT secret FROM credentials; /private/file password=synthetic-secret");
        var result = runner(service).run(task("event-a"), context());
        assertEquals("Event execution failed", result.getMessage());
        assertEquals("result.plugin.event-failed", result.getMessageMetadata().messageKey());
        assertFalse(result.getMessage().contains("synthetic-secret"));
    }

    @Test
    void manualExecutionShouldPersistConfiguredEventOnce() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        EventTaskRunner runner = runner(receiptService);
        TaskContext context = context();

        TaskResult result = runner.run(task("event-a"), context);

        assertTrue(result.isSuccess());
        assertEquals("event-a", receiptService.command.eventCode());
        assertEquals("TASK:1", receiptService.command.sourceKey());
        assertTrue(context.containsEvent("event-a"));
    }

    @Test
    void sameInboundEventShouldPreventRepublishing() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        EventTaskRunner runner = runner(receiptService);
        TaskContext context = context();
        context.addEvent("event-a");

        TaskResult result = runner.run(task("event-a"), context);

        assertTrue(result.isSuccess());
        assertEquals(0, receiptService.calls);
    }

    @Test
    void differentInboundEventShouldNotSuppressPublishing() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        EventTaskRunner runner = runner(receiptService);
        TaskContext context = context();
        context.addEvent("event-b");

        TaskResult result = runner.run(task("event-a"), context);

        assertTrue(result.isSuccess());
        assertEquals(1, receiptService.calls);
    }

    @Test
    void fileProbeShouldPublishOnlyStableMatchingFiles() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        EventTaskRunner runner =
                new EventTaskRunner(
                        receiptService,
                        null,
                        new StubResourceResolver(),
                        new PluginConfigCodec(new ObjectMapper()));

        TaskResult result = runner.run(fileProbeTask(), context());

        assertTrue(result.isSuccess());
        assertEquals(1, receiptService.calls);
        assertEquals("FILE:7", receiptService.command.sourceKey());
        assertEquals(
                "/in/ready.csv",
                ((java.util.Map<?, ?>) receiptService.command.payload()).get("path"));
    }

    @Test
    void permanentBusinessFailureShouldNotBeRetried() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        receiptService.failure = new BusinessException(ResultCode.PARAM_ERROR, "invalid event");

        TaskResult result = runner(receiptService).run(task("event-a"), context());

        assertEquals("Event execution failed", result.getMessage());
        assertEquals("IJOB-PLUGIN-0049", result.getMessageMetadata().errorCode());
        assertFalse(result.isRetryableFailure());
    }

    @Test
    void waitShouldPreferExactInboundEvent() {
        CapturingWaitService waitService = new CapturingWaitService(EventWaitStatus.MATCHED);
        EventTaskRunner runner =
                new EventTaskRunner(
                        new CapturingReceiptService(),
                        waitService,
                        null,
                        new PluginConfigCodec(new ObjectMapper()));
        TaskContext context = context();
        context.seedInboundEvent(99L, "event-a");

        TaskResult result = runner.run(waitTask("event-a"), context);

        assertTrue(result.isSuccess());
        assertEquals(99L, waitService.registration.preferredEventId());
    }

    @Test
    void rejectedExistingApprovalShouldFail() {
        CapturingWaitService waitService = new CapturingWaitService(EventWaitStatus.REJECTED);
        EventTaskRunner runner =
                new EventTaskRunner(
                        new CapturingReceiptService(),
                        waitService,
                        null,
                        new PluginConfigCodec(new ObjectMapper()));

        TaskResult result = runner.run(approvalTask(), context());

        assertEquals(com.ifielsc.ijob.contract.enums.InstanceStatus.FAILED, result.getStatus());
        assertFalse(result.isRetryableFailure());
    }

    @Test
    void duplicateFileShouldNotConsumePublishLimit() {
        CapturingReceiptService receiptService = new CapturingReceiptService();
        receiptService.duplicateCalls = 1;
        ResourceResolver resolver =
                new StubResourceResolver() {
                    @Override
                    protected List<FileInfo> entries() {
                        return List.of(stable("first.csv"), stable("second.csv"));
                    }
                };
        EventTaskRunner runner =
                new EventTaskRunner(
                        receiptService, null, resolver, new PluginConfigCodec(new ObjectMapper()));

        TaskResult result = runner.run(fileProbeTask(1), context());

        assertTrue(result.isSuccess());
        assertEquals(2, receiptService.calls);
        assertTrue(result.getMessage().contains("Published 1"));
        assertTrue(result.getMessage().contains("skipped 1"));
    }

    @Test
    void permanentFileClientFailureShouldNotBeRetried() {
        ResourceResolver resolver =
                new StubResourceResolver() {
                    @Override
                    protected List<FileInfo> entries() throws IOException {
                        throw new FileClientException("permission denied", false, null);
                    }
                };
        EventTaskRunner runner =
                new EventTaskRunner(
                        new CapturingReceiptService(),
                        null,
                        resolver,
                        new PluginConfigCodec(new ObjectMapper()));

        TaskResult result = runner.run(fileProbeTask(), context());

        assertFalse(result.isRetryableFailure());
        assertEquals("File probe failed", result.getMessage());
        assertEquals("IJOB-PLUGIN-0053", result.getMessageMetadata().errorCode());
    }

    private EventTaskRunner runner(EventReceiptService receiptService) {
        return new EventTaskRunner(
                receiptService, null, null, new PluginConfigCodec(new ObjectMapper()));
    }

    private TaskExecutionSpec task(String eventCode) {
        return new TaskExecutionSpec(
                "event-task",
                PluginType.EVENT,
                "{\"eventCode\":\"" + eventCode + "\"}",
                0,
                0,
                null,
                true,
                null);
    }

    private TaskExecutionSpec fileProbeTask() {
        return fileProbeTask(100);
    }

    private TaskExecutionSpec fileProbeTask(int maxMatches) {
        return new TaskExecutionSpec(
                "file-probe",
                PluginType.EVENT,
                "{\"action\":\"PROBE_FILE\",\"eventCode\":\"file.ready\","
                        + "\"fileResourceId\":7,\"filePath\":\"/in\","
                        + "\"filePattern\":\"*.csv\","
                        + "\"minStableAgeSeconds\":30,"
                        + "\"maxMatches\":"
                        + maxMatches
                        + "}",
                0,
                0,
                null,
                true,
                null);
    }

    private TaskExecutionSpec waitTask(String eventCode) {
        return configuredTask(
                "{\"action\":\"WAIT_EVENT\"," + "\"eventCode\":\"" + eventCode + "\"}");
    }

    private TaskExecutionSpec approvalTask() {
        return configuredTask(
                "{\"action\":\"WAIT_APPROVAL\"," + "\"allowedOperatorIds\":[\"reviewer\"]}");
    }

    private TaskExecutionSpec configuredTask(String config) {
        return new TaskExecutionSpec(
                "event-task", PluginType.EVENT, config, 0, 0, null, true, null);
    }

    private static FileInfo stable(String name) {
        return FileInfo.builder()
                .name(name)
                .size(12)
                .lastModified(LocalDateTime.now().minusMinutes(2))
                .build();
    }

    private TaskContext context() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private static final class CapturingReceiptService extends EventReceiptService {
        private EventPublishCommand command;
        private int calls;
        private int duplicateCalls;
        private RuntimeException failure;

        private CapturingReceiptService() {
            super(null, null, null, null, new ObjectMapper());
        }

        @Override
        public EventReceiptResult receive(EventPublishCommand command) {
            if (failure != null) {
                throw failure;
            }
            this.command = command;
            calls++;
            return new EventReceiptResult(10L, calls <= duplicateCalls);
        }
    }

    private static class StubResourceResolver extends ResourceResolver {
        private StubResourceResolver() {
            super(null);
        }

        @Override
        public ResourceClientLease<FileClient> leaseFileClient(Long resourceId) {
            FileClient client =
                    new FileClient() {
                        @Override
                        public List<FileInfo> list(String path) throws IOException {
                            return entries();
                        }

                        @Override
                        public java.io.InputStream download(String path) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void upload(String path, java.io.InputStream input) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void mkdirs(String path) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public boolean exists(String path) {
                            return false;
                        }

                        @Override
                        public void delete(String path) {
                            throw new UnsupportedOperationException();
                        }
                    };
            return new ResourceClientLease<>(client, () -> {});
        }

        protected List<FileInfo> entries() throws IOException {
            return List.of(
                    stable("ready.csv"),
                    FileInfo.builder()
                            .name("fresh.csv")
                            .size(8)
                            .lastModified(LocalDateTime.now())
                            .build(),
                    stable("ready.txt"));
        }
    }

    private static final class CapturingWaitService extends EventWaitService {
        private final EventWaitStatus status;
        private EventWaitRegistration registration;

        private CapturingWaitService(EventWaitStatus status) {
            super(null, null, null, new ObjectMapper());
            this.status = status;
        }

        @Override
        public EventWaitRegistrationResult register(
                Long instanceId, EventWaitRegistration registration) {
            this.registration = registration;
            return new EventWaitRegistrationResult(
                    status, status == EventWaitStatus.MATCHED ? 99L : null);
        }
    }
}
