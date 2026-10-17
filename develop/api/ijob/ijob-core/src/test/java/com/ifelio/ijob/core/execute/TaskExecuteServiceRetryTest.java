package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.execute.task.TaskRunnerFactory;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.execute.variable.VariableSnapshotService;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class TaskExecuteServiceRetryTest {
    @Test
    void retryShouldDiscardAttemptOutputsAndReleaseResources() throws Exception {
        assertRetry(false);
    }

    @Test
    void retryableFailureResultShouldStartAnotherAttempt() throws Exception {
        assertRetry(true);
    }

    private void assertRetry(boolean returnFailureResult) throws Exception {
        Instance instance = instance();
        RecordingRunner runner = new RecordingRunner(returnFailureResult);
        RecordingRuntime runtime = new RecordingRuntime();
        StubInstances instances = new StubInstances(instance);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskExecuteService service =
                new TaskExecuteService(
                        new VariableResolver(),
                        new VariableSnapshotService(new ObjectMapper()),
                        new StubDefinitions(spec()),
                        instances,
                        null,
                        runtime,
                        TaskExecutionLockFixture.withoutPolicies(),
                        new TaskRunnerFactory(List.of(runner)),
                        new ExecutionManager(),
                        null,
                        executor);

        try {
            service.execute(instance.getId());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(2, runner.runCount.get());
        assertEquals(1, runner.closedResources.get());
        assertEquals(instance.getId(), runtime.successInstanceId);
        assertEquals("{\"final\":\"fresh\"}", instances.outputVariables);
    }

    private static Instance instance() {
        Instance instance = new Instance();
        instance.setId(91L);
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(19L);
        instance.setTaskDefinitionId(51L);
        return instance;
    }

    private static TaskExecutionSpec spec() {
        return new TaskExecutionSpec(
                "retry-task", PluginType.SCRIPT, "config", 1, 0, 60, true, null);
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
        private String outputVariables;

        private StubInstances(Instance instance) {
            super(null, null, null, null);
            this.instance = instance;
        }

        @Override
        public Instance getInstance(Long id) {
            return instance;
        }

        @Override
        public void updateOutputVariables(Long instanceId, String outputVariables) {
            this.outputVariables = outputVariables;
        }
    }

    private static final class RecordingRuntime extends InstanceRuntimeService {
        private Long successInstanceId;

        private RecordingRuntime() {
            super(null, null);
        }

        @Override
        public void running(Long instanceId) {}

        @Override
        public void completeTask(Long instanceId, TaskResult result) {
            assertEquals(com.ifelio.ijob.contract.enums.InstanceStatus.SUCCESS, result.getStatus());
            successInstanceId = instanceId;
        }

        @Override
        public void fail(Long instanceId, String message) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingRunner implements TaskRunner {
        private final AtomicInteger runCount = new AtomicInteger();
        private final AtomicInteger closedResources = new AtomicInteger();
        private final boolean returnFailureResult;

        private RecordingRunner(boolean returnFailureResult) {
            this.returnFailureResult = returnFailureResult;
        }

        @Override
        public PluginType supportType() {
            return PluginType.SCRIPT;
        }

        @Override
        public TaskResult run(TaskExecutionSpec spec, TaskContext context) {
            int attempt = runCount.incrementAndGet();
            if (attempt == 1) {
                context.putVariable("attempt-output", "stale");
                context.registerResource(closedResources::incrementAndGet);
                if (returnFailureResult) {
                    return TaskResult.retryableFail("retry");
                }
                throw new IllegalStateException("retry");
            }

            assertFalse(context.containsVariable("attempt-output"));
            assertEquals(1, closedResources.get());
            context.putVariable("final", "fresh");
            return TaskResult.success();
        }
    }
}
