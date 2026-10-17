package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.response.MessageDefinition;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerStatus;
import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.job.definition.JobExecutionSpec;
import com.ifelio.ijob.core.trigger.TriggerCommand;
import com.ifelio.ijob.core.trigger.TriggerCommandHandler;
import com.ifelio.ijob.core.trigger.TriggerRuntimeService;
import com.ifelio.ijob.infra.entity.runtime.Trigger;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

class ExecutionCommandRejectionTest {
    @Test
    void shouldFailWaitingTriggerWhenTriggerPoolRejects() {
        RecordingTriggerRuntimeService runtimeService = new RecordingTriggerRuntimeService();
        TriggerCommandHandler handler =
                new TriggerCommandHandler(rejectingExecutor(), null, runtimeService);

        assertThrows(
                RejectedExecutionException.class, () -> handler.handle(new TriggerCommand(17L)));

        assertEquals(17L, runtimeService.failedTriggerId);
        assertEquals("Trigger execution rejected by capacity limit", runtimeService.message);
    }

    @Test
    void shouldLeaveDurableEventTriggerWaitingWhenPoolRejects() {
        RecordingTriggerRuntimeService runtimeService = new RecordingTriggerRuntimeService();
        runtimeService.durableEvent = true;
        TriggerCommandHandler handler =
                new TriggerCommandHandler(rejectingExecutor(), null, runtimeService);

        assertThrows(
                RejectedExecutionException.class, () -> handler.handle(new TriggerCommand(20L)));

        assertNull(runtimeService.failedTriggerId);
    }

    @Test
    void shouldFailClaimedJobRetryWhenTriggerPoolRejects() {
        RecordingInstanceRuntimeService runtimeService = new RecordingInstanceRuntimeService();
        JobRetryCommandHandler handler =
                new JobRetryCommandHandler(rejectingExecutor(), null, runtimeService);

        assertThrows(RejectedExecutionException.class, () -> handler.handle(command(18L)));

        assertEquals(18L, runtimeService.failedInstanceId);
        assertEquals("Job retry rejected by capacity limit", runtimeService.message);
    }

    @Test
    void shouldFailClaimedJobRetryWhenAsyncExecutionFails() {
        RecordingInstanceRuntimeService runtimeService = new RecordingInstanceRuntimeService();
        JobRetryCommandHandler handler =
                new JobRetryCommandHandler(
                        (Executor) Runnable::run, new FailingJobExecuteService(), runtimeService);

        handler.handle(command(19L));

        assertEquals(19L, runtimeService.failedInstanceId);
        assertEquals("Job retry execution failed", runtimeService.message);
    }

    private static Executor rejectingExecutor() {
        return task -> {
            throw new RejectedExecutionException("saturated");
        };
    }

    private static JobRetryCommand command(Long instanceId) {
        JobExecutionNode node =
                new JobExecutionNode(
                        61L,
                        70L,
                        "node-61",
                        "Node 61",
                        TargetType.TASK,
                        161L,
                        ExecuteCondition.ALL_SUCCESS,
                        FailStrategy.STOP);
        JobExecutionPlan plan =
                new JobExecutionPlan(
                        70L,
                        List.of(node),
                        Map.of(61L, node),
                        Map.of(61L, List.of()),
                        Map.of(61L, List.of()),
                        Map.of(61L, 0),
                        List.of(node));
        return new JobRetryCommand(
                instanceId, new JobExecutionSpec(7L, 70L, 3L, "job", 60), plan, Set.of(61L));
    }

    private static final class RecordingTriggerRuntimeService extends TriggerRuntimeService {
        private Long failedTriggerId;
        private String message;
        private boolean durableEvent;

        private RecordingTriggerRuntimeService() {
            super(null);
        }

        @Override
        public void completeReason(Long id, TriggerStatus status, MessageDefinition reason) {
            assertEquals(TriggerStatus.FAILED, status);
            org.junit.jupiter.api.Assertions.assertTrue(
                    reason.messageKey().startsWith("runtime.reason."));
            failedTriggerId = id;
            message = reason.defaultMessage();
        }

        @Override
        public Trigger getTrigger(Long id) {
            Trigger trigger = new Trigger();
            trigger.setId(id);
            trigger.setEventId(durableEvent ? 88L : null);
            return trigger;
        }
    }

    private static final class RecordingInstanceRuntimeService extends InstanceRuntimeService {
        private Long failedInstanceId;
        private String message;

        private RecordingInstanceRuntimeService() {
            super(null, null);
        }

        @Override
        public void completeTask(Long instanceId, TaskResult result) {
            org.junit.jupiter.api.Assertions.assertNotNull(result.getMessageMetadata());
            assertEquals(InstanceStatus.FAILED, result.getStatus());
            failedInstanceId = instanceId;
            message = result.getMessage();
        }
    }

    private static final class FailingJobExecuteService extends JobExecuteService {
        private FailingJobExecuteService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void executeRetry(
                Long instanceId,
                com.ifelio.ijob.core.job.definition.JobExecutionSpec spec,
                com.ifelio.ijob.core.execute.graph.JobExecutionPlan plan,
                Set<Long> retryNodeIds) {
            throw new IllegalStateException("execution failed");
        }
    }
}
