package com.ifelio.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.GatewayMode;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.DagNodeExecutor;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.ExecutionManager;
import com.ifelio.ijob.core.execute.ExecutionTreeControl;
import com.ifelio.ijob.core.execute.NodeExecuteResult;
import com.ifelio.ijob.core.execute.NodeExecuteStatus;
import com.ifelio.ijob.core.execute.condition.GatewayDecisionResolver;
import com.ifelio.ijob.core.execute.condition.GatewaySelection;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class DagCoordinatorTest {
    private final List<String> logMessages = new ArrayList<>();

    private com.ifelio.ijob.core.instance.InstanceLogService recordingLogs() {
        return new com.ifelio.ijob.core.instance.InstanceLogService(null) {
            @Override
            public void info(Long id, String content) {
                assertEquals(99L, id);
                logMessages.add(content);
            }
        };
    }

    @Test
    void historyResultShouldReleaseRetryDescendant() {
        JobExecutionNode first = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode second = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionPlan plan = plan(List.of(first, second), Map.of(1L, List.of(2L)));
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(Map.of(2L, NodeExecuteStatus.SUCCESS));

        DagCoordinationResult result =
                coordinate(
                        plan,
                        Map.of(1L, InstanceStatus.SUCCESS),
                        nodeExecutor,
                        new RecordingTreeControl());

        assertFalse(result.abortedByFailure());
        assertEquals(
                Map.of(
                        1L, InstanceStatus.SUCCESS,
                        2L, InstanceStatus.SUCCESS),
                result.nodeResults());
        assertEquals(List.of(2L), nodeExecutor.executedIds);
        assertTrue(
                logMessages.stream()
                        .anyMatch(
                                value ->
                                        value.contains("dispatched")
                                                && value.contains("definitionNodeId=2")));
        assertTrue(
                logMessages.stream()
                        .anyMatch(
                                value ->
                                        value.contains("resolved")
                                                && value.contains("status=SUCCESS")));
    }

    @Test
    void skipFailureShouldContinueAndKeepRootFailed() {
        JobExecutionNode first = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.SKIP);
        JobExecutionNode second = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionPlan plan = plan(List.of(first, second), Map.of(1L, List.of(2L)));
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(Map.of(1L, NodeExecuteStatus.FAILED));

        DagCoordinationResult result =
                coordinate(plan, Map.of(), nodeExecutor, new RecordingTreeControl());
        DagExecutionResult aggregated = new DagResultAggregator().aggregate(result, 2);

        assertFalse(result.abortedByFailure());
        assertEquals(InstanceStatus.FAILED, aggregated.status());
        assertEquals(List.of(2L), nodeExecutor.skippedIds);
    }

    @Test
    void stopFailureShouldAbortWithoutMarkingRootStopped() {
        JobExecutionNode first = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode second = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.SKIP);
        JobExecutionPlan plan = plan(List.of(first, second), Map.of());
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(
                        Map.of(
                                1L, NodeExecuteStatus.FAILED,
                                2L, NodeExecuteStatus.SUCCESS));
        RecordingTreeControl treeControl = new RecordingTreeControl();

        DagCoordinationResult result = coordinate(plan, Map.of(), nodeExecutor, treeControl);
        DagExecutionResult aggregated = new DagResultAggregator().aggregate(result, 2);

        assertTrue(result.abortedByFailure());
        assertEquals(InstanceStatus.FAILED, aggregated.status());
        assertEquals(List.of(99L), treeControl.abortedRootIds);
    }

    @Test
    void unexpectedAsyncExceptionShouldBecomeNodeFailure() {
        JobExecutionNode first = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.SKIP);
        JobExecutionNode second = node(2L, ExecuteCondition.ALWAYS, FailStrategy.STOP);
        JobExecutionPlan plan = plan(List.of(first, second), Map.of(1L, List.of(2L)));
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(Map.of(2L, NodeExecuteStatus.SUCCESS));
        nodeExecutor.throwingNodeId = 1L;

        DagCoordinationResult result =
                coordinate(plan, Map.of(), nodeExecutor, new RecordingTreeControl());

        assertFalse(result.abortedByFailure());
        assertEquals(InstanceStatus.FAILED, result.nodeResults().get(1L));
        assertEquals(InstanceStatus.SUCCESS, result.nodeResults().get(2L));
    }

    @Test
    void multipleReadyNodesShouldExecuteInParallel() throws InterruptedException {
        JobExecutionNode first = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode second = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionPlan plan = plan(List.of(first, second), Map.of());
        ParallelNodeExecutor nodeExecutor = new ParallelNodeExecutor();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            DagCoordinationResult result =
                    coordinate(plan, Map.of(), nodeExecutor, new RecordingTreeControl(), executor);

            assertFalse(result.abortedByFailure());
            assertEquals(2, nodeExecutor.maxActive.get());
            assertEquals(2, result.nodeResults().size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void stopFailureShouldCancelRunningSibling() throws InterruptedException {
        JobExecutionNode failing = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode sibling = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.SKIP);
        JobExecutionPlan plan = plan(List.of(failing, sibling), Map.of());
        CancellingNodeExecutor nodeExecutor = new CancellingNodeExecutor();
        RecordingTreeControl treeControl = new RecordingTreeControl();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            DagCoordinationResult result =
                    coordinate(plan, Map.of(), nodeExecutor, treeControl, executor);

            assertTrue(result.abortedByFailure());
            assertEquals(InstanceStatus.FAILED, result.nodeResults().get(1L));
            assertTrue(nodeExecutor.siblingInterrupted.await(2, TimeUnit.SECONDS));
            assertEquals(List.of(99L), treeControl.abortedRootIds);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void waitingEventShouldSuspendWithoutReleasingDescendant() {
        JobExecutionNode waiting = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode descendant = node(2L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionPlan plan = plan(List.of(waiting, descendant), Map.of(1L, List.of(2L)));
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(
                        Map.of(
                                1L, NodeExecuteStatus.WAITING_EVENT,
                                2L, NodeExecuteStatus.SUCCESS));

        DagCoordinationResult result =
                coordinate(plan, Map.of(), nodeExecutor, new RecordingTreeControl());

        assertTrue(result.suspended());
        assertFalse(result.abortedByFailure());
        assertTrue(result.nodeResults().isEmpty());
        assertEquals(List.of(1L), nodeExecutor.executedIds);
    }

    @Test
    void exclusiveGatewayShouldSkipInactiveBranchAndReleaseJoinFromActiveParentsOnly() {
        JobExecutionNode source = node(1L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode gateway = gateway(2L);
        JobExecutionNode selected = node(3L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode inactive = node(4L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionNode join = node(5L, ExecuteCondition.ALL_SUCCESS, FailStrategy.STOP);
        JobExecutionPlan plan = routedPlan(source, gateway, selected, inactive, join);
        RecordingNodeExecutor nodeExecutor =
                new RecordingNodeExecutor(
                        Map.of(
                                1L, NodeExecuteStatus.SUCCESS,
                                3L, NodeExecuteStatus.SUCCESS,
                                5L, NodeExecuteStatus.SUCCESS));
        GatewayDecisionResolver resolver =
                (jobInstanceId, gatewayNode, executionPlan, instances, forceReevaluate) ->
                        new GatewaySelection(java.util.Set.of(12L), false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            DagCoordinator coordinator =
                    new DagCoordinator(
                            new DependencyEvaluator(),
                            nodeExecutor,
                            new RecordingTreeControl(),
                            resolver,
                            executor);
            TaskContext context =
                    new TaskContext(
                            99L,
                            new ExecutionHandle(99L),
                            null,
                            recordingLogs(),
                            new VariableResolver());

            DagCoordinationResult result = awaitCoordination(coordinator, plan, Map.of(), context);

            assertFalse(result.abortedByFailure());
            assertEquals(InstanceStatus.SUCCESS, result.nodeResults().get(2L));
            assertEquals(InstanceStatus.SKIPPED, result.nodeResults().get(4L));
            assertEquals(InstanceStatus.SUCCESS, result.nodeResults().get(5L));
            assertEquals(List.of(1L, 3L, 5L), nodeExecutor.executedIds);
            assertEquals(List.of(4L), nodeExecutor.skippedIds);
        } finally {
            executor.shutdownNow();
        }
    }

    private DagCoordinationResult coordinate(
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> history,
            RecordingNodeExecutor nodeExecutor,
            RecordingTreeControl treeControl) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return coordinate(plan, history, nodeExecutor, treeControl, executor);
        } finally {
            executor.shutdownNow();
        }
    }

    private DagCoordinationResult coordinate(
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> history,
            DagNodeExecutor nodeExecutor,
            RecordingTreeControl treeControl,
            ExecutorService executor) {
        DagCoordinator coordinator =
                new DagCoordinator(new DependencyEvaluator(), nodeExecutor, treeControl, executor);
        TaskContext context =
                new TaskContext(
                        99L,
                        new ExecutionHandle(99L),
                        null,
                        recordingLogs(),
                        new VariableResolver());
        return awaitCoordination(coordinator, plan, history, context);
    }

    private DagCoordinationResult awaitCoordination(
            DagCoordinator coordinator,
            JobExecutionPlan plan,
            Map<Long, InstanceStatus> history,
            TaskContext context) {
        ExecutorService continuations = Executors.newSingleThreadExecutor();
        var result =
                coordinator.coordinateAsync(
                        99L,
                        plan,
                        history,
                        java.util.Set.of(),
                        java.util.Set.of(),
                        new java.util.concurrent.ConcurrentHashMap<>(),
                        context,
                        ignored -> {},
                        continuations,
                        System.nanoTime(),
                        null);
        try {
            return result.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException failure) {
            throw new AssertionError(failure);
        } finally {
            result.cancel(true);
            continuations.shutdownNow();
        }
    }

    private static JobExecutionNode node(
            Long id, ExecuteCondition condition, FailStrategy failStrategy) {
        return new JobExecutionNode(
                id,
                10L,
                "node-" + id,
                "Node " + id,
                TargetType.TASK,
                100L + id,
                condition,
                failStrategy);
    }

    private static JobExecutionNode gateway(Long id) {
        return new JobExecutionNode(
                id,
                10L,
                "node-" + id,
                "Gateway " + id,
                JobNodeKind.GATEWAY,
                GatewayMode.EXCLUSIVE,
                null,
                null,
                null,
                null,
                ExecuteCondition.ALWAYS,
                FailStrategy.STOP);
    }

    private static JobExecutionPlan routedPlan(
            JobExecutionNode source,
            JobExecutionNode gateway,
            JobExecutionNode selected,
            JobExecutionNode inactive,
            JobExecutionNode join) {
        List<JobExecutionNode> nodes = List.of(source, gateway, selected, inactive, join);
        List<JobExecutionEdge> edges =
                List.of(
                        new JobExecutionEdge(11L, 10L, 1L, 2L, null, null, false, null, null),
                        new JobExecutionEdge(12L, 10L, 2L, 3L, "selected", 10, false, 1, null),
                        new JobExecutionEdge(13L, 10L, 2L, 4L, "default", 20, true, null, null),
                        new JobExecutionEdge(14L, 10L, 3L, 5L, null, null, false, null, null),
                        new JobExecutionEdge(15L, 10L, 4L, 5L, null, null, false, null, null));
        Map<Long, JobExecutionNode> nodeMap = new HashMap<>();
        nodes.forEach(node -> nodeMap.put(node.id(), node));
        Map<Long, List<Long>> parentMap =
                Map.of(
                        1L, List.of(),
                        2L, List.of(1L),
                        3L, List.of(2L),
                        4L, List.of(2L),
                        5L, List.of(3L, 4L));
        Map<Long, List<Long>> childMap =
                Map.of(
                        1L, List.of(2L),
                        2L, List.of(3L, 4L),
                        3L, List.of(5L),
                        4L, List.of(5L),
                        5L, List.of());
        Map<Long, List<JobExecutionEdge>> incoming = new HashMap<>();
        Map<Long, List<JobExecutionEdge>> outgoing = new HashMap<>();
        nodes.forEach(
                node -> {
                    incoming.put(node.id(), new ArrayList<>());
                    outgoing.put(node.id(), new ArrayList<>());
                });
        edges.forEach(
                edge -> {
                    incoming.get(edge.toNodeId()).add(edge);
                    outgoing.get(edge.fromNodeId()).add(edge);
                });
        return new JobExecutionPlan(
                10L,
                nodes,
                nodeMap,
                parentMap,
                childMap,
                Map.of(1L, 0, 2L, 1, 3L, 1, 4L, 1, 5L, 2),
                List.of(source),
                edges,
                Map.of(
                        11L,
                        edges.get(0),
                        12L,
                        edges.get(1),
                        13L,
                        edges.get(2),
                        14L,
                        edges.get(3),
                        15L,
                        edges.get(4)),
                incoming,
                outgoing);
    }

    private static JobExecutionPlan plan(
            List<JobExecutionNode> nodes, Map<Long, List<Long>> specifiedChildren) {
        Map<Long, JobExecutionNode> nodeMap = new HashMap<>();
        Map<Long, List<Long>> childMap = new HashMap<>();
        Map<Long, List<Long>> parentMap = new HashMap<>();
        for (JobExecutionNode node : nodes) {
            nodeMap.put(node.id(), node);
            childMap.put(node.id(), new ArrayList<>());
            parentMap.put(node.id(), new ArrayList<>());
        }
        specifiedChildren.forEach(
                (parentId, childIds) -> {
                    childMap.get(parentId).addAll(childIds);
                    childIds.forEach(childId -> parentMap.get(childId).add(parentId));
                });
        Map<Long, Integer> indegreeMap = new HashMap<>();
        nodes.forEach(node -> indegreeMap.put(node.id(), parentMap.get(node.id()).size()));
        List<JobExecutionNode> startNodes =
                nodes.stream().filter(node -> indegreeMap.get(node.id()) == 0).toList();
        return new JobExecutionPlan(
                10L, nodes, nodeMap, parentMap, childMap, indegreeMap, startNodes);
    }

    private static final class RecordingNodeExecutor implements DagNodeExecutor {
        private final Map<Long, NodeExecuteStatus> statuses;
        private final List<Long> executedIds = new ArrayList<>();
        private final List<Long> skippedIds = new ArrayList<>();
        private Long throwingNodeId;

        private RecordingNodeExecutor(Map<Long, NodeExecuteStatus> statuses) {
            this.statuses = statuses;
        }

        @Override
        public NodeExecuteResult execute(
                JobExecutionNode node,
                Long parentInstanceId,
                Map<Long, Instance> nodeInstanceMap,
                TaskContext controlContext) {
            executedIds.add(node.id());
            if (node.id().equals(throwingNodeId)) {
                throw new IllegalStateException("boom");
            }
            return NodeExecuteResult.of(node, statuses.get(node.id()));
        }

        @Override
        public NodeExecuteResult skip(
                JobExecutionNode node, Long parentInstanceId, Map<Long, Instance> nodeInstanceMap) {
            skippedIds.add(node.id());
            return NodeExecuteResult.of(node, NodeExecuteStatus.SKIPPED);
        }
    }

    private static final class ParallelNodeExecutor implements DagNodeExecutor {
        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();

        @Override
        public NodeExecuteResult execute(
                JobExecutionNode node,
                Long parentInstanceId,
                Map<Long, Instance> nodeInstanceMap,
                TaskContext controlContext) {
            int activeNow = active.incrementAndGet();
            maxActive.accumulateAndGet(activeNow, Math::max);
            bothStarted.countDown();
            try {
                if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Ready nodes did not start concurrently");
                }
                return NodeExecuteResult.of(node, NodeExecuteStatus.SUCCESS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                active.decrementAndGet();
            }
        }

        @Override
        public NodeExecuteResult skip(
                JobExecutionNode node, Long parentInstanceId, Map<Long, Instance> nodeInstanceMap) {
            throw new AssertionError("Ready node must execute");
        }
    }

    private static final class CancellingNodeExecutor implements DagNodeExecutor {
        private final CountDownLatch siblingStarted = new CountDownLatch(1);
        private final CountDownLatch siblingInterrupted = new CountDownLatch(1);

        @Override
        public NodeExecuteResult execute(
                JobExecutionNode node,
                Long parentInstanceId,
                Map<Long, Instance> nodeInstanceMap,
                TaskContext controlContext) {
            if (node.id().equals(1L)) {
                try {
                    if (!siblingStarted.await(2, TimeUnit.SECONDS)) {
                        throw new AssertionError("Sibling node did not start");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                return NodeExecuteResult.of(node, NodeExecuteStatus.FAILED);
            }
            siblingStarted.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Sibling was not cancelled");
            } catch (InterruptedException exception) {
                siblingInterrupted.countDown();
                Thread.currentThread().interrupt();
                return NodeExecuteResult.of(node, NodeExecuteStatus.STOPPED);
            }
        }

        @Override
        public NodeExecuteResult skip(
                JobExecutionNode node, Long parentInstanceId, Map<Long, Instance> nodeInstanceMap) {
            throw new AssertionError("Ready node must execute");
        }
    }

    private static final class RecordingTreeControl extends ExecutionTreeControl {
        private final List<Long> abortedRootIds = new ArrayList<>();

        private RecordingTreeControl() {
            super(new ExecutionManager(), null);
        }

        @Override
        public void requestAbortDescendants(Long rootInstanceId) {
            abortedRootIds.add(rootInstanceId);
        }
    }
}
