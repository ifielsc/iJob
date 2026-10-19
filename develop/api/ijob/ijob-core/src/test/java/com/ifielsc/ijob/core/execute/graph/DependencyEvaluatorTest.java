package com.ifielsc.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DependencyEvaluatorTest {
    private final DependencyEvaluator evaluator = new DependencyEvaluator();

    @Test
    void rootNodeShouldExecuteWithoutParentResult() {
        JobExecutionNode root = node(1L, ExecuteCondition.ALL_SUCCESS);

        assertTrue(evaluator.shouldExecute(root, plan(root, Map.of(1L, List.of())), Map.of()));
    }

    @Test
    void allSuccessShouldAcceptSuccessAndSkippedParents() {
        JobExecutionNode child = node(3L, ExecuteCondition.ALL_SUCCESS);

        assertTrue(
                evaluator.shouldExecute(
                        child,
                        plan(child, Map.of(3L, List.of(1L, 2L))),
                        Map.of(
                                1L, InstanceStatus.SUCCESS,
                                2L, InstanceStatus.SKIPPED)));
        assertFalse(
                evaluator.shouldExecute(
                        child,
                        plan(child, Map.of(3L, List.of(1L, 2L))),
                        Map.of(
                                1L, InstanceStatus.SUCCESS,
                                2L, InstanceStatus.FAILED)));
    }

    @Test
    void anySuccessAndAlwaysShouldStillWaitForAllParentsToFinish() {
        JobExecutionNode any = node(3L, ExecuteCondition.ANY_SUCCESS);
        JobExecutionPlan anyPlan = plan(any, Map.of(3L, List.of(1L, 2L)));

        assertFalse(evaluator.shouldExecute(any, anyPlan, Map.of(1L, InstanceStatus.SUCCESS)));
        assertTrue(
                evaluator.shouldExecute(
                        any,
                        anyPlan,
                        Map.of(
                                1L, InstanceStatus.SUCCESS,
                                2L, InstanceStatus.FAILED)));

        JobExecutionNode always = node(3L, ExecuteCondition.ALWAYS);
        assertTrue(
                evaluator.shouldExecute(
                        always,
                        plan(always, Map.of(3L, List.of(1L, 2L))),
                        Map.of(
                                1L, InstanceStatus.FAILED,
                                2L, InstanceStatus.TIMED_OUT)));
    }

    private static JobExecutionNode node(Long id, ExecuteCondition condition) {
        return new JobExecutionNode(
                id,
                10L,
                "node-" + id,
                "Node " + id,
                TargetType.TASK,
                100L + id,
                condition,
                FailStrategy.SKIP);
    }

    private static JobExecutionPlan plan(JobExecutionNode node, Map<Long, List<Long>> parentMap) {
        return new JobExecutionPlan(
                10L,
                List.of(node),
                Map.of(node.id(), node),
                parentMap,
                Map.of(node.id(), List.of()),
                Map.of(node.id(), parentMap.get(node.id()).size()),
                parentMap.get(node.id()).isEmpty() ? List.of(node) : List.of());
    }
}
