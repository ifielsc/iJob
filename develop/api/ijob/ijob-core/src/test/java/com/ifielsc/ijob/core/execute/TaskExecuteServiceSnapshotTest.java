// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.execute.task.TaskRunnerFactory;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class TaskExecuteServiceSnapshotTest {
    @Test
    void shouldExecuteOnlyTheDefinitionReferencedByInstance() throws Exception {
        Instance instance = new Instance();
        instance.setId(81L);
        instance.setTargetType(TargetType.TASK);
        instance.setTargetId(9L);
        instance.setTaskDefinitionId(41L);
        TaskExecutionSpec spec = spec();
        RecordingRuntimeDefinitions definitions = new RecordingRuntimeDefinitions(spec);
        RecordingRunner runner = new RecordingRunner();
        RecordingInstanceRuntime runtime = new RecordingInstanceRuntime();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskExecuteService service =
                new TaskExecuteService(
                        new VariableResolver(),
                        new VariableSnapshotService(new ObjectMapper()),
                        definitions,
                        new StubInstanceService(instance),
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

        assertEquals(9L, definitions.taskId);
        assertEquals(41L, definitions.definitionId);
        assertSame(spec, runner.spec);
        assertEquals(81L, runtime.successInstanceId);
    }

    private static TaskExecutionSpec spec() {
        return new TaskExecutionSpec(
                "task", PluginType.SCRIPT, "immutable-config", 0, 0, 60, true, null);
    }

    private static final class RecordingRuntimeDefinitions extends RuntimeDefinitionService {
        private final TaskExecutionSpec spec;
        private Long taskId;
        private Long definitionId;

        private RecordingRuntimeDefinitions(TaskExecutionSpec spec) {
            super(null, null);
            this.spec = spec;
        }

        @Override
        public TaskAdmissionSnapshot loadTask(Long id, Long taskDefinitionId) {
            taskId = id;
            definitionId = taskDefinitionId;
            return new TaskAdmissionSnapshot(id, taskDefinitionId, 3L, spec);
        }

        @Override
        public JobExecutionSpec loadJob(Long jobId, Long jobDefinitionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubInstanceService extends InstanceService {
        private final Instance instance;

        private StubInstanceService(Instance instance) {
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

    private static final class RecordingInstanceRuntime extends InstanceRuntimeService {
        private Long successInstanceId;

        private RecordingInstanceRuntime() {
            super(null, null);
        }

        @Override
        public void running(Long instanceId) {}

        @Override
        public void completeTask(Long instanceId, TaskResult result) {
            assertEquals(
                    com.ifielsc.ijob.contract.enums.InstanceStatus.SUCCESS, result.getStatus());
            successInstanceId = instanceId;
        }

        @Override
        public void fail(Long instanceId, String message) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingRunner implements TaskRunner {
        private TaskExecutionSpec spec;

        @Override
        public PluginType supportType() {
            return PluginType.SCRIPT;
        }

        @Override
        public TaskResult run(TaskExecutionSpec executionSpec, TaskContext context) {
            spec = executionSpec;
            return TaskResult.success();
        }
    }
}
