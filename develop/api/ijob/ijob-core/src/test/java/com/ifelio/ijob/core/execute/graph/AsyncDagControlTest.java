package com.ifelio.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.DagNodeExecutor;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.ExecutionManager;
import com.ifelio.ijob.core.execute.ExecutionTreeControl;
import com.ifelio.ijob.core.execute.NodeExecuteResult;
import com.ifelio.ijob.core.execute.NodeExecuteStatus;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.instance.InstanceLogService;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class AsyncDagControlTest {
    @Test
    void stopAndTimeoutMustInterruptActiveLeafAndCompleteOrchestration() throws Exception {
        for (boolean timeout : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                var result = fixture.start(timeout ? 1 : null);
                assertTrue(fixture.started.await(2, TimeUnit.SECONDS));
                if (!timeout) fixture.handle.requestStop();
                assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
                assertTrue(fixture.interrupted.await(1, TimeUnit.SECONDS));
                assertEquals(timeout, fixture.handle.isTimeoutRequested());
            }
        }
    }

    @Test
    void pausedOrchestratorMustNotOccupyItsOnlyWorker() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.handle.requestPause();
            var result = fixture.start(null);
            assertEquals(
                    "available", fixture.jobs.submit(() -> "available").get(1, TimeUnit.SECONDS));
            assertEquals(1, fixture.started.getCount());
            fixture.handle.resume();
            assertTrue(fixture.started.await(2, TimeUnit.SECONDS));
            fixture.handle.requestStop();
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectedLeafOrContinuationMustNotLeaveAnUncompletedFuture() throws Exception {
        for (boolean rejectLeaf : List.of(false, true)) {
            try (var fixture = new Fixture()) {
                if (rejectLeaf) fixture.leaves.shutdown();
                else fixture.jobs.shutdown();
                var result = fixture.start(null);
                assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void shortSerialChainMustWakeImmediatelyOnCompletion() throws Exception {
        try (var fixture = new Fixture()) {
            var nodes = new java.util.ArrayList<JobExecutionNode>();
            var byId = new java.util.HashMap<Long, JobExecutionNode>();
            var parents = new java.util.HashMap<Long, List<Long>>();
            var children = new java.util.HashMap<Long, List<Long>>();
            var counts = new java.util.HashMap<Long, Integer>();
            for (long id = 1; id <= 80; id++) {
                var node =
                        new JobExecutionNode(
                                id,
                                1L,
                                "n" + id,
                                "node",
                                TargetType.TASK,
                                1L,
                                ExecuteCondition.ALL_SUCCESS,
                                FailStrategy.STOP);
                nodes.add(node);
                byId.put(id, node);
                parents.put(id, id == 1 ? List.of() : List.of(id - 1));
                children.put(id, id == 80 ? List.of() : List.of(id + 1));
                counts.put(id, id == 1 ? 0 : 1);
            }
            DagNodeExecutor fast =
                    new DagNodeExecutor() {
                        @Override
                        public NodeExecuteResult execute(
                                JobExecutionNode node,
                                Long parent,
                                Map<Long, Instance> instances,
                                TaskContext context) {
                            return NodeExecuteResult.of(node, NodeExecuteStatus.SUCCESS);
                        }

                        @Override
                        public NodeExecuteResult skip(
                                JobExecutionNode node, Long parent, Map<Long, Instance> instances) {
                            return NodeExecuteResult.of(node, NodeExecuteStatus.SKIPPED);
                        }
                    };
            var plan =
                    new JobExecutionPlan(
                            1L, nodes, byId, parents, children, counts, List.of(nodes.getFirst()));
            var context =
                    new TaskContext(
                            1L,
                            fixture.handle,
                            null,
                            new InstanceLogService(null) {
                                @Override
                                public void info(Long id, String message) {}
                            },
                            new VariableResolver());
            var result =
                    new DagCoordinator(
                                    new DependencyEvaluator(), fast, fixture.tree, fixture.leaves)
                            .coordinateAsync(
                                    1L,
                                    plan,
                                    Map.of(),
                                    Set.of(),
                                    Set.of(),
                                    new ConcurrentHashMap<>(),
                                    context,
                                    ignored -> {},
                                    fixture.jobs,
                                    System.nanoTime(),
                                    null);
            assertEquals(80, result.get(3, TimeUnit.SECONDS).nodeResults().size());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ThreadPoolExecutor jobs = pool();
        final ThreadPoolExecutor leaves = pool();
        final ExecutionHandle handle = new ExecutionHandle(1L);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch interrupted = new CountDownLatch(1);
        final JobExecutionNode node =
                new JobExecutionNode(
                        1L,
                        1L,
                        "n",
                        "node",
                        TargetType.TASK,
                        1L,
                        ExecuteCondition.ALL_SUCCESS,
                        FailStrategy.STOP);
        final ExecutionTreeControl tree =
                new ExecutionTreeControl(new ExecutionManager(), null) {
                    @Override
                    public void requestTimeout(Long id) {
                        handle.requestTimeout();
                    }

                    @Override
                    public void requestAbortDescendants(Long id) {}
                };
        final DagNodeExecutor nodeExecutor =
                new DagNodeExecutor() {
                    @Override
                    public NodeExecuteResult execute(
                            JobExecutionNode node,
                            Long parent,
                            Map<Long, Instance> instances,
                            TaskContext context) {
                        started.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException failure) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                            throw new StopException();
                        }
                        return NodeExecuteResult.of(node, NodeExecuteStatus.SUCCESS);
                    }

                    @Override
                    public NodeExecuteResult skip(
                            JobExecutionNode node, Long parent, Map<Long, Instance> instances) {
                        return NodeExecuteResult.of(node, NodeExecuteStatus.SKIPPED);
                    }
                };

        CompletableFuture<DagCoordinationResult> start(Integer timeout) {
            var context =
                    new TaskContext(
                            1L,
                            handle,
                            null,
                            new InstanceLogService(null) {
                                @Override
                                public void info(Long id, String message) {}
                            },
                            new VariableResolver());
            var plan =
                    new JobExecutionPlan(
                            1L,
                            List.of(node),
                            Map.of(1L, node),
                            Map.of(1L, List.of()),
                            Map.of(1L, List.of()),
                            Map.of(1L, 0),
                            List.of(node));
            return new DagCoordinator(new DependencyEvaluator(), nodeExecutor, tree, leaves)
                    .coordinateAsync(
                            1L,
                            plan,
                            Map.of(),
                            Set.of(),
                            Set.of(),
                            new ConcurrentHashMap<>(),
                            context,
                            ignored -> {},
                            jobs,
                            System.nanoTime(),
                            timeout);
        }

        private static ThreadPoolExecutor pool() {
            return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));
        }

        @Override
        public void close() throws InterruptedException {
            handle.requestStop();
            jobs.shutdownNow();
            leaves.shutdownNow();
            assertTrue(jobs.awaitTermination(2, TimeUnit.SECONDS));
            assertTrue(leaves.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
