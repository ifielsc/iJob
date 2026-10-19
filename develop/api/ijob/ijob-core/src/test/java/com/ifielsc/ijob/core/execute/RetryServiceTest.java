package com.ifielsc.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlanBuilder;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class RetryServiceTest {
    @Test
    void taskRetryShouldResetAndExecuteFailedSnapshot() {
        RecordingInstances instances = new RecordingInstances();
        instances.root = instance(9L, TargetType.TASK, InstanceStatus.FAILED, null);
        RecordingTaskExecute executor = new RecordingTaskExecute();
        TaskRetryService service = new TaskRetryService(instances, executor);

        service.retry(9L);

        assertEquals(List.of(9L), instances.resetIds);
        assertEquals(9L, executor.id);

        instances.root.setStatus(InstanceStatus.SUCCESS);
        BusinessException error = assertThrows(BusinessException.class, () -> service.retry(9L));
        assertEquals(ResultCode.CONFLICT, error.getCode());
    }

    @Test
    void taskRetryShouldRejectDuplicateClaim() {
        RecordingInstances instances = new RecordingInstances();
        instances.root = instance(9L, TargetType.TASK, InstanceStatus.FAILED, null);
        instances.resetResult = false;
        RecordingTaskExecute executor = new RecordingTaskExecute();
        TaskRetryService service = new TaskRetryService(instances, executor);

        BusinessException error = assertThrows(BusinessException.class, () -> service.retry(9L));

        assertEquals(ResultCode.CONFLICT, error.getCode());
        assertEquals(null, executor.id);
    }

    @Test
    void jobRetryShouldResetFailedBranchAndRootBeforeExecute() {
        RecordingInstances instances = new RecordingInstances();
        instances.root = instance(20L, TargetType.JOB, InstanceStatus.FAILED, null);
        instances.root.setTargetId(7L);
        instances.root.setDefinitionId(70L);
        instances.children =
                List.of(
                        instance(21L, TargetType.TASK, InstanceStatus.FAILED, 61L),
                        instance(22L, TargetType.TASK, InstanceStatus.SKIPPED, 62L));
        JobExecutionSpec spec = new JobExecutionSpec(7L, 70L, 3L, "job", 60);
        JobExecutionPlan plan = plan();
        RecordingJobRetryDispatcher dispatcher = new RecordingJobRetryDispatcher();
        JobRetryService service =
                new JobRetryService(
                        instances,
                        new RecordingDefinitions(spec),
                        new RecordingPlanBuilder(plan),
                        new JobRetryPlanner(),
                        dispatcher,
                        new ExecutionTreeControl(new ExecutionManager(), instances));

        service.retryFailedNodes(20L);

        assertEquals(List.of(20L, 21L, 22L), instances.resetIds);
        assertSame(spec, dispatcher.command.spec());
        assertSame(plan, dispatcher.command.plan());
        assertEquals(Set.of(61L, 62L), dispatcher.command.retryNodeIds());
    }

    @Test
    void concurrentJobRetryShouldDispatchOnlyClaimWinner() throws InterruptedException {
        Instance root = instance(20L, TargetType.JOB, InstanceStatus.FAILED, null);
        root.setTargetId(7L);
        root.setDefinitionId(70L);
        List<Instance> children =
                List.of(
                        instance(21L, TargetType.TASK, InstanceStatus.FAILED, 61L),
                        instance(22L, TargetType.TASK, InstanceStatus.SKIPPED, 62L));
        ConcurrentClaimInstances instances = new ConcurrentClaimInstances(root, children);
        JobExecutionSpec spec = new JobExecutionSpec(7L, 70L, 3L, "job", 60);
        CountingJobRetryDispatcher dispatcher = new CountingJobRetryDispatcher();
        JobRetryService service =
                new JobRetryService(
                        instances,
                        new RecordingDefinitions(spec),
                        new RecordingPlanBuilder(plan()),
                        new JobRetryPlanner(),
                        dispatcher,
                        new ExecutionTreeControl(new ExecutionManager(), instances));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<?>> attempts =
                    List.of(
                            executor.submit(() -> service.retryFailedNodes(20L)),
                            executor.submit(() -> service.retryFailedNodes(20L)));
            int conflicts = 0;
            for (Future<?> attempt : attempts) {
                try {
                    attempt.get();
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof BusinessException);
                    BusinessException businessException = (BusinessException) exception.getCause();
                    assertEquals(ResultCode.CONFLICT, businessException.getCode());
                    conflicts++;
                }
            }

            assertEquals(1, conflicts);
            assertEquals(1, dispatcher.dispatchCount.get());
            assertEquals(Set.of(21L, 22L), instances.resetIds);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    private static Instance instance(
            Long id, TargetType type, InstanceStatus status, Long definitionNodeId) {
        Instance instance = new Instance();
        instance.setId(id);
        instance.setTargetType(type);
        instance.setStatus(status);
        instance.setDefinitionNodeId(definitionNodeId);
        instance.setRootInstanceId(id);
        return instance;
    }

    private static final class RecordingInstances extends InstanceService {
        private Instance root;
        private List<Instance> children = List.of();
        private final List<Long> resetIds = new ArrayList<>();
        private boolean resetResult = true;

        private RecordingInstances() {
            super(null, null, null, null);
        }

        @Override
        public Instance getInstance(Long id) {
            if (root != null && id.equals(root.getId())) {
                return root;
            }
            return children.stream()
                    .filter(child -> id.equals(child.getId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<Instance> listChildren(Long id) {
            return root != null && id.equals(root.getId()) ? children : List.of();
        }

        @Override
        public boolean claimRetry(Long id) {
            resetIds.add(id);
            return resetResult;
        }

        @Override
        public void resetJobBranch(Long id) {
            resetIds.add(id);
        }
    }

    private static final class ConcurrentClaimInstances extends InstanceService {
        private final Instance root;
        private final List<Instance> children;
        private final CountDownLatch claimers = new CountDownLatch(2);
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final Set<Long> resetIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private ConcurrentClaimInstances(Instance root, List<Instance> children) {
            super(null, null, null, null);
            this.root = root;
            this.children = children;
        }

        @Override
        public Instance getInstance(Long id) {
            if (id.equals(root.getId())) {
                return root;
            }
            return children.stream()
                    .filter(child -> id.equals(child.getId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<Instance> listChildren(Long id) {
            return id.equals(root.getId()) ? children : List.of();
        }

        @Override
        public boolean claimRetry(Long id) {
            claimers.countDown();
            try {
                if (!claimers.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Retry attempts did not claim concurrently");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return claimed.compareAndSet(false, true);
        }

        @Override
        public void resetJobBranch(Long id) {
            resetIds.add(id);
        }
    }

    private static final class RecordingTaskExecute extends TaskExecuteService {
        private Long id;

        private RecordingTaskExecute() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void execute(Long instanceId) {
            id = instanceId;
        }
    }

    private static final class RecordingDefinitions extends RuntimeDefinitionService {
        private final JobExecutionSpec spec;

        private RecordingDefinitions(JobExecutionSpec spec) {
            super(null, null);
            this.spec = spec;
        }

        @Override
        public JobExecutionSpec loadJob(Long jobId, Long definitionId) {
            return spec;
        }
    }

    private static JobExecutionPlan plan() {
        JobExecutionNode first = node(61L);
        JobExecutionNode second = node(62L);
        return new JobExecutionPlan(
                70L,
                List.of(first, second),
                Map.of(61L, first, 62L, second),
                Map.of(61L, List.of(), 62L, List.of(61L)),
                Map.of(61L, List.of(62L), 62L, List.of()),
                Map.of(61L, 0, 62L, 1),
                List.of(first));
    }

    private static JobExecutionNode node(Long id) {
        return new JobExecutionNode(
                id,
                70L,
                "node-" + id,
                "Node " + id,
                TargetType.TASK,
                100L + id,
                ExecuteCondition.ALL_SUCCESS,
                FailStrategy.STOP);
    }

    private static final class RecordingPlanBuilder extends JobExecutionPlanBuilder {
        private final JobExecutionPlan plan;

        private RecordingPlanBuilder(JobExecutionPlan plan) {
            super(null, null, null);
            this.plan = plan;
        }

        @Override
        public JobExecutionPlan build(Long definitionId) {
            return plan;
        }
    }

    private static final class RecordingJobRetryDispatcher extends JobRetryCommandDispatcher {
        private JobRetryCommand command;

        private RecordingJobRetryDispatcher() {
            super(null);
        }

        @Override
        public void dispatchAfterCommit(JobRetryCommand selectedCommand) {
            command = selectedCommand;
        }
    }

    private static final class CountingJobRetryDispatcher extends JobRetryCommandDispatcher {
        private final AtomicInteger dispatchCount = new AtomicInteger();

        private CountingJobRetryDispatcher() {
            super(null);
        }

        @Override
        public void dispatchAfterCommit(JobRetryCommand command) {
            dispatchCount.incrementAndGet();
        }
    }
}
