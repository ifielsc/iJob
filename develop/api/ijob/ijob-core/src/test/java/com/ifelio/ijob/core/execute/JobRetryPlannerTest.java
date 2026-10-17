package com.ifelio.ijob.core.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class JobRetryPlannerTest {
    private final JobRetryPlanner planner = new JobRetryPlanner();

    @Test
    void failedRootShouldRetryFailedNodesAndDescendants() {
        JobRetryPlan retryPlan =
                planner.plan(
                        InstanceStatus.FAILED,
                        plan(),
                        List.of(
                                instance(21L, 61L, InstanceStatus.FAILED),
                                instance(22L, 62L, InstanceStatus.SKIPPED),
                                instance(23L, 63L, InstanceStatus.SUCCESS)));

        assertEquals(Set.of(61L, 62L), retryPlan.retryNodeIds());
        assertEquals(List.of(21L, 22L), retryPlan.resetInstanceIds());
    }

    @Test
    void timedOutRootShouldRetryWholeDefinition() {
        JobRetryPlan retryPlan =
                planner.plan(
                        InstanceStatus.TIMED_OUT,
                        plan(),
                        List.of(instance(23L, 63L, InstanceStatus.SUCCESS)));

        assertEquals(Set.of(61L, 62L, 63L), retryPlan.retryNodeIds());
        assertEquals(List.of(23L), retryPlan.resetInstanceIds());
    }

    @Test
    void failedRootShouldRejectMissingFailureAndInvalidSnapshot() {
        BusinessException noFailure =
                assertThrows(
                        BusinessException.class,
                        () ->
                                planner.plan(
                                        InstanceStatus.FAILED,
                                        plan(),
                                        List.of(instance(21L, 61L, InstanceStatus.SUCCESS))));
        BusinessException invalidSnapshot =
                assertThrows(
                        BusinessException.class,
                        () ->
                                planner.plan(
                                        InstanceStatus.FAILED,
                                        plan(),
                                        List.of(instance(21L, 99L, InstanceStatus.FAILED))));

        assertEquals(ResultCode.CONFLICT, noFailure.getCode());
        assertEquals(ResultCode.SYSTEM_ERROR, invalidSnapshot.getCode());
    }

    @Test
    void failedRootShouldRetryStoppedAbortSiblingAndWholePlanOnDemand() {
        JobRetryPlan failed =
                planner.plan(
                        InstanceStatus.FAILED,
                        plan(),
                        List.of(
                                instance(21L, 61L, InstanceStatus.FAILED),
                                instance(23L, 63L, InstanceStatus.STOPPED)));
        JobRetryPlan whole =
                planner.planWhole(plan(), List.of(instance(23L, 63L, InstanceStatus.SUCCESS)));

        assertEquals(Set.of(61L, 62L, 63L), failed.retryNodeIds());
        assertEquals(List.of(21L, 23L), failed.resetInstanceIds());
        assertEquals(Set.of(61L, 62L, 63L), whole.retryNodeIds());
        assertEquals(List.of(23L), whole.resetInstanceIds());
    }

    private static JobExecutionPlan plan() {
        JobExecutionNode first = node(61L);
        JobExecutionNode second = node(62L);
        JobExecutionNode third = node(63L);
        return new JobExecutionPlan(
                70L,
                List.of(first, second, third),
                Map.of(61L, first, 62L, second, 63L, third),
                Map.of(
                        61L, List.of(),
                        62L, List.of(61L),
                        63L, List.of()),
                Map.of(
                        61L, List.of(62L),
                        62L, List.of(),
                        63L, List.of()),
                Map.of(61L, 0, 62L, 1, 63L, 0),
                List.of(first, third));
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

    private static Instance instance(Long id, Long definitionNodeId, InstanceStatus status) {
        Instance instance = new Instance();
        instance.setId(id);
        instance.setDefinitionNodeId(definitionNodeId);
        instance.setStatus(status);
        return instance;
    }
}
