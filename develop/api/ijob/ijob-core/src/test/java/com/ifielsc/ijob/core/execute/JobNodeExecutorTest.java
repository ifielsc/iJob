// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class JobNodeExecutorTest {
    @Test
    void taskShouldReceiveFrozenDirectUpstreamOutputs() {
        RecordingInstances instances = new RecordingInstances();
        JobNodeExecutor executor =
                new JobNodeExecutor(
                        instances,
                        new RecordingTaskExecute(instances, InstanceStatus.SUCCESS, false),
                        new RecordingJobExecute(instances, InstanceStatus.SUCCESS),
                        variableSnapshots());
        JobExecutionNode upstream =
                new JobExecutionNode(
                        60L,
                        51L,
                        "upstream",
                        "Upstream",
                        TargetType.TASK,
                        8L,
                        ExecuteCondition.ALL_SUCCESS,
                        FailStrategy.STOP);
        Instance upstreamInstance = new Instance();
        upstreamInstance.setId(80L);
        upstreamInstance.setDefinitionNodeId(60L);
        upstreamInstance.setStatus(InstanceStatus.SUCCESS);
        upstreamInstance.setOutputVariables("{\"upstream\":\"ready\"}");
        Map<Long, Instance> nodeInstances = new ConcurrentHashMap<>();
        nodeInstances.put(60L, upstreamInstance);

        executor.execute(
                node(TargetType.TASK, FailStrategy.STOP),
                90L,
                nodeInstances,
                context(),
                List.of(upstream));

        assertEquals(
                "{\"batch\":7,\"upstream\":\"ready\"}",
                instances.values.get(81L).getInputVariables());
    }

    @Test
    void taskSuccessShouldReturnTerminalResultWithNodePolicy() {
        RecordingInstances instances = new RecordingInstances();
        RecordingTaskExecute taskExecute =
                new RecordingTaskExecute(instances, InstanceStatus.SUCCESS, false);
        JobNodeExecutor executor =
                new JobNodeExecutor(
                        instances,
                        taskExecute,
                        new RecordingJobExecute(instances, InstanceStatus.SUCCESS),
                        variableSnapshots());

        NodeExecuteResult result =
                executor.execute(
                        node(TargetType.TASK, FailStrategy.SKIP),
                        90L,
                        new ConcurrentHashMap<>(),
                        context());

        assertEquals(NodeExecuteStatus.SUCCESS, result.status());
        assertEquals(FailStrategy.SKIP, result.failStrategy());
        assertEquals(81L, taskExecute.executedId);
    }

    @Test
    void nestedJobFailureShouldKeepSkipPolicy() {
        RecordingInstances instances = new RecordingInstances();
        JobNodeExecutor executor =
                new JobNodeExecutor(
                        instances,
                        new RecordingTaskExecute(instances, InstanceStatus.SUCCESS, false),
                        new RecordingJobExecute(instances, InstanceStatus.FAILED),
                        variableSnapshots());

        NodeExecuteResult result =
                executor.execute(
                        node(TargetType.JOB, FailStrategy.SKIP),
                        90L,
                        new ConcurrentHashMap<>(),
                        context());

        assertEquals(NodeExecuteStatus.FAILED, result.status());
        assertEquals(FailStrategy.SKIP, result.failStrategy());
    }

    @Test
    void unexpectedTaskExceptionShouldFailActiveChild() {
        RecordingInstances instances = new RecordingInstances();
        JobNodeExecutor executor =
                new JobNodeExecutor(
                        instances,
                        new RecordingTaskExecute(instances, InstanceStatus.SUCCESS, true),
                        new RecordingJobExecute(instances, InstanceStatus.SUCCESS),
                        variableSnapshots());

        NodeExecuteResult result =
                executor.execute(
                        node(TargetType.TASK, FailStrategy.STOP),
                        90L,
                        new ConcurrentHashMap<>(),
                        context());

        assertEquals(NodeExecuteStatus.FAILED, result.status());
        assertEquals(InstanceStatus.FAILED, instances.values.get(81L).getStatus());
    }

    @Test
    void dependencySkipShouldPersistSkippedChild() {
        RecordingInstances instances = new RecordingInstances();
        JobNodeExecutor executor =
                new JobNodeExecutor(
                        instances,
                        new RecordingTaskExecute(instances, InstanceStatus.SUCCESS, false),
                        new RecordingJobExecute(instances, InstanceStatus.SUCCESS),
                        variableSnapshots());

        NodeExecuteResult result =
                executor.skip(
                        node(TargetType.TASK, FailStrategy.STOP), 90L, new ConcurrentHashMap<>());

        assertEquals(NodeExecuteStatus.SKIPPED, result.status());
        assertEquals(InstanceStatus.SKIPPED, instances.values.get(81L).getStatus());
    }

    private static JobExecutionNode node(TargetType targetType, FailStrategy failStrategy) {
        return new JobExecutionNode(
                61L,
                51L,
                "node-61",
                "Node 61",
                targetType,
                9L,
                ExecuteCondition.ALL_SUCCESS,
                failStrategy);
    }

    private static TaskContext context() {
        return new TaskContext(90L, new ExecutionHandle(90L), null, null, new VariableResolver());
    }

    private static VariableSnapshotService variableSnapshots() {
        return new VariableSnapshotService(new ObjectMapper());
    }

    private static final class RecordingInstances extends InstanceService {
        private final Map<Long, Instance> values = new ConcurrentHashMap<>();

        private RecordingInstances() {
            super(null, null, null, null);
            Instance parent = new Instance();
            parent.setId(90L);
            parent.setInputVariables("{\"batch\":7}");
            values.put(parent.getId(), parent);
        }

        @Override
        public Long createChild(
                Long parentInstanceId, JobExecutionNode node, String inputVariables) {
            Instance child = new Instance();
            child.setId(81L);
            child.setDefinitionNodeId(node.id());
            child.setTargetType(node.nodeType());
            child.setStatus(InstanceStatus.WAITING);
            child.setInputVariables(inputVariables);
            values.put(child.getId(), child);
            return child.getId();
        }

        @Override
        public Instance getInstance(Long id) {
            return values.get(id);
        }

        @Override
        public void skipped(Long instanceId) {
            values.get(instanceId).setStatus(InstanceStatus.SKIPPED);
        }

        @Override
        public boolean completeTask(
                Long instanceId,
                InstanceStatus status,
                String message,
                RuntimeErrorMetadata metadata) {
            org.junit.jupiter.api.Assertions.assertNotNull(metadata);
            fail(instanceId, message);
            return true;
        }

        @Override
        public void fail(Long instanceId, String message) {
            values.get(instanceId).setStatus(InstanceStatus.FAILED);
        }

        @Override
        public void stop(Long instanceId) {
            values.get(instanceId).setStatus(InstanceStatus.STOPPED);
        }

        @Override
        public void updateInputVariables(Long instanceId, String inputVariables) {
            values.get(instanceId).setInputVariables(inputVariables);
        }
    }

    private static final class RecordingTaskExecute extends TaskExecuteService {
        private final RecordingInstances instances;
        private final InstanceStatus resultStatus;
        private final boolean fail;
        private Long executedId;

        private RecordingTaskExecute(
                RecordingInstances instances, InstanceStatus resultStatus, boolean fail) {
            super(
                    new VariableResolver(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            this.instances = instances;
            this.resultStatus = resultStatus;
            this.fail = fail;
        }

        @Override
        public void execute(Long instanceId) {
            executedId = instanceId;
            if (fail) {
                throw new IllegalStateException("boom");
            }
            instances.values.get(instanceId).setStatus(resultStatus);
        }
    }

    private static final class RecordingJobExecute extends JobExecuteService {
        private final RecordingInstances instances;
        private final InstanceStatus resultStatus;

        private RecordingJobExecute(RecordingInstances instances, InstanceStatus resultStatus) {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
            this.instances = instances;
            this.resultStatus = resultStatus;
        }

        @Override
        public void execute(Long instanceId) {
            instances.values.get(instanceId).setStatus(resultStatus);
        }
    }
}
