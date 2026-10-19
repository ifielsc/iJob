package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.execute.task.TaskRunnerFactory;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class TaskExecuteServiceTimeoutTest {
    @Test
    void timeoutShouldNotBeRetriedOrRecordedAsFailure() throws Exception {
        Instance instance = new Instance();
        instance.setId(81L);
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(9L);
        instance.setTaskDefinitionId(41L);
        TaskExecutionSpec spec =
                new TaskExecutionSpec(
                        "timeout-task", PluginType.SCRIPT, "config", 2, 0, 1, true, null);
        BlockingRunner runner = new BlockingRunner();
        RecordingRuntime runtime = new RecordingRuntime();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskExecuteService service =
                new TaskExecuteService(
                        new VariableResolver(),
                        new VariableSnapshotService(new ObjectMapper()),
                        new StubDefinitions(spec),
                        new StubInstances(instance),
                        null,
                        runtime,
                        TaskExecutionLockFixture.withoutPolicies(),
                        new TaskRunnerFactory(List.of(runner)),
                        new ExecutionManager(),
                        null,
                        executor);

        try {
            service.execute(81L);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, runner.runCount.get());
        assertEquals(1, runner.stopCount.get());
        assertEquals(81L, runtime.timeoutInstanceId);
        assertEquals("Task timeout", runtime.message);
        org.junit.jupiter.api.Assertions.assertNotNull(runtime.metadata);
        assertEquals("runtime.reason.task-timeout", runtime.metadata.messageKey());
    }

    private static final class StubDefinitions extends RuntimeDefinitionService {
        private final TaskExecutionSpec spec;

        private StubDefinitions(TaskExecutionSpec spec) {
            super(null, null);
            this.spec = spec;
        }

        @Override
        public TaskAdmissionSnapshot loadTask(Long taskId, Long taskDefinitionId) {
            return new TaskAdmissionSnapshot(taskId, taskDefinitionId, 3L, spec);
        }
    }

    private static final class StubInstances extends InstanceService {
        private final Instance instance;

        private StubInstances(Instance instance) {
            super(null, null, null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }

        @Override
        public void updateOutputVariables(Long instanceId, String outputVariables) {}
    }

    private static final class RecordingRuntime extends InstanceRuntimeService {
        private Long timeoutInstanceId;
        private String message;
        private RuntimeErrorMetadata metadata;

        private RecordingRuntime() {
            super(null, null);
        }

        @Override
        public void running(Long instanceId) {}

        @Override
        public void timeout(Long instanceId, String timeoutMessage) {
            timeoutInstanceId = instanceId;
            message = timeoutMessage;
        }

        @Override
        public void completeTask(Long instanceId, TaskResult result) {
            assertEquals(InstanceStatus.TIMED_OUT, result.getStatus());
            timeoutInstanceId = instanceId;
            message = result.getMessage();
            metadata = result.getMessageMetadata();
        }

        @Override
        public void success(Long instanceId) {
            throw new AssertionError("timeout recorded as success");
        }

        @Override
        public void fail(Long instanceId, String errorMessage) {
            throw new AssertionError("timeout recorded as failure");
        }

        @Override
        public void stop(Long instanceId) {
            throw new AssertionError("timeout recorded as stop");
        }
    }

    private static final class BlockingRunner implements TaskRunner {
        private final AtomicInteger runCount = new AtomicInteger();
        private final AtomicInteger stopCount = new AtomicInteger();

        @Override
        public PluginType supportType() {
            return PluginType.SCRIPT;
        }

        @Override
        public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
            runCount.incrementAndGet();
            try {
                Thread.sleep(10_000L);
                return TaskResult.success();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", exception);
            }
        }

        @Override
        public void stop(TaskExecutionSpec spec, TaskContext context) {
            stopCount.incrementAndGet();
        }
    }
}
