// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.graph.DagCoordinator;
import com.ifielsc.ijob.core.execute.graph.DagResultAggregator;
import com.ifielsc.ijob.core.execute.graph.DependencyEvaluator;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlanBuilder;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class NestedJobCapacityTest {
    @Test
    void nestedJobsMustReleaseBothLeafAndOrchestrationThreads() throws Exception {
        runNested(8, 2, 1);
        runNested(8, 2, 20);
        runNested(8, 4, 1);
    }

    private void runNested(int width, int levels, int jobWorkers) throws Exception {
        var jobs =
                new ThreadPoolExecutor(
                        jobWorkers,
                        jobWorkers,
                        0,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(500));
        var leaves =
                new ThreadPoolExecutor(8, 32, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500));
        var caller = Executors.newSingleThreadExecutor();
        var instances = new Instances();
        var manager = new ExecutionManager();
        var control = new ExecutionTreeControl(manager, instances);
        var nodes = new AtomicReference<JobNodeExecutor>();
        DagNodeExecutor bridge =
                (DagNodeExecutor)
                        Proxy.newProxyInstance(
                                DagNodeExecutor.class.getClassLoader(),
                                new Class<?>[] {DagNodeExecutor.class},
                                (proxy, method, args) -> method.invoke(nodes.get(), args));
        var coordinator = new DagCoordinator(new DependencyEvaluator(), bridge, control, leaves);
        var definitions =
                new RuntimeDefinitionService(null, null) {
                    @Override
                    public JobExecutionSpec loadJob(Long id, Long definition) {
                        return new JobExecutionSpec(id, definition, 1L, "nested", 10);
                    }
                };
        var builder =
                new JobExecutionPlanBuilder(null, null, null) {
                    @Override
                    public JobExecutionPlan build(Long definition) {
                        return plan(
                                definition,
                                definition == 1L ? width : 1,
                                definition < levels ? TargetType.JOB : TargetType.TASK);
                    }
                };
        var runtime =
                new InstanceRuntimeService(null, null) {
                    @Override
                    public void running(Long id) {
                        instances.status(id, InstanceStatus.RUNNING);
                    }

                    @Override
                    public void success(Long id) {
                        instances.status(id, InstanceStatus.SUCCESS);
                    }

                    @Override
                    public void fail(Long id, String message) {
                        instances.status(id, InstanceStatus.FAILED);
                    }

                    @Override
                    public void stop(Long id) {
                        instances.status(id, InstanceStatus.STOPPED);
                    }

                    @Override
                    public void timeout(Long id, String message) {
                        instances.status(id, InstanceStatus.TIMED_OUT);
                    }
                };
        var logs =
                new InstanceLogService(null) {
                    @Override
                    public void info(Long id, String message) {}

                    @Override
                    public void warn(Long id, String message) {}

                    @Override
                    public void error(Long id, String message) {}
                };
        var service =
                new JobExecuteService(
                        new VariableResolver(),
                        new JobRuntimeNestingGuard(null) {
                            @Override
                            public void validateInstance(Instance instance) {}
                        },
                        new VariableSnapshotService(new ObjectMapper()),
                        definitions,
                        builder,
                        coordinator,
                        new DagResultAggregator(),
                        instances,
                        logs,
                        runtime,
                        manager,
                        control,
                        jobs);
        var tasks =
                new TaskExecuteService(
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
                        null) {
                    @Override
                    public void execute(Long id) {
                        instances.status(id, InstanceStatus.SUCCESS);
                    }
                };
        nodes.set(
                new JobNodeExecutor(
                        instances,
                        tasks,
                        service,
                        new VariableSnapshotService(new ObjectMapper())));
        var run = caller.submit(() -> service.execute(1L));
        try {
            run.get(4, TimeUnit.SECONDS);
            assertEquals(InstanceStatus.SUCCESS, instances.getInstance(1L).getStatus());
            assertEquals(1 + width * levels, instances.values.size());
            assertTrue(
                    instances.values.values().stream()
                            .allMatch(i -> i.getStatus() == InstanceStatus.SUCCESS));
            assertTrue(jobs.getLargestPoolSize() <= jobWorkers);
        } finally {
            run.cancel(true);
            control.requestStop(1L);
            jobs.shutdownNow();
            leaves.shutdownNow();
            caller.shutdownNow();
            assertTrue(jobs.awaitTermination(3, TimeUnit.SECONDS));
            assertTrue(leaves.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static JobExecutionPlan plan(Long definition, int count, TargetType type) {
        List<JobExecutionNode> nodes = new ArrayList<>();
        Map<Long, JobExecutionNode> byId = new HashMap<>();
        Map<Long, List<Long>> links = new HashMap<>();
        Map<Long, Integer> indegrees = new HashMap<>();
        for (long id = 1; id <= count; id++) {
            var node =
                    new JobExecutionNode(
                            id,
                            definition,
                            "n" + id,
                            "node",
                            type,
                            definition + 1,
                            ExecuteCondition.ALL_SUCCESS,
                            FailStrategy.STOP);
            nodes.add(node);
            byId.put(id, node);
            links.put(id, List.of());
            indegrees.put(id, 0);
        }
        return new JobExecutionPlan(definition, nodes, byId, links, links, indegrees, nodes);
    }

    private static final class Instances extends InstanceService {
        final Map<Long, Instance> values = new ConcurrentHashMap<>();
        final AtomicLong sequence = new AtomicLong(1);

        Instances() {
            super(null, null, null, null);
            Instance root = new Instance();
            root.setId(1L);
            root.setDefinitionId(1L);
            root.setTargetId(1L);
            root.setTargetType(TargetType.JOB);
            root.setStatus(InstanceStatus.WAITING);
            values.put(1L, root);
        }

        void status(Long id, InstanceStatus status) {
            values.get(id).setStatus(status);
        }

        @Override
        public Instance getInstance(Long id) {
            return values.get(id);
        }

        @Override
        public List<Instance> listChildren(Long id) {
            return values.values().stream()
                    .filter(i -> id.equals(i.getParentInstanceId()))
                    .toList();
        }

        @Override
        public Long createChild(Long parent, JobExecutionNode node, String inputs) {
            var child = new Instance();
            child.setId(sequence.incrementAndGet());
            child.setParentInstanceId(parent);
            child.setDefinitionNodeId(node.id());
            child.setDefinitionId(node.refId());
            child.setTargetId(node.refId());
            child.setTargetType(node.nodeType());
            child.setInputVariables(inputs);
            child.setStatus(InstanceStatus.WAITING);
            values.put(child.getId(), child);
            return child.getId();
        }

        @Override
        public void updateProgress(Long id, Integer value) {}

        @Override
        public void updateOutputVariables(Long id, String value) {
            values.get(id).setOutputVariables(value);
        }

        @Override
        public void fail(Long id, String message) {
            status(id, InstanceStatus.FAILED);
        }

        @Override
        public void stop(Long id) {
            status(id, InstanceStatus.STOPPED);
        }
    }
}
