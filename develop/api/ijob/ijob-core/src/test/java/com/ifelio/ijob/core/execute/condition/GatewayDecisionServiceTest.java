package com.ifelio.ijob.core.execute.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.DagConditionOperator;
import com.ifelio.ijob.contract.enums.ExecuteCondition;
import com.ifelio.ijob.contract.enums.FailStrategy;
import com.ifelio.ijob.contract.enums.GatewayDecisionStatus;
import com.ifelio.ijob.contract.enums.GatewayMode;
import com.ifelio.ijob.contract.enums.JobNodeKind;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerType;
import com.ifelio.ijob.contract.job.DagConditionExpression;
import com.ifelio.ijob.contract.job.DagConditionOperand;
import com.ifelio.ijob.core.execute.graph.JobExecutionEdge;
import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.core.execute.variable.VariableSnapshotService;
import com.ifelio.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;
import com.ifelio.ijob.infra.mapper.runtime.TriggerMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class GatewayDecisionServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPersistAndReuseSuccessfulDecision() {
        RecordingDecisionMapper decisions = new RecordingDecisionMapper();
        JobExecutionNode gateway = gateway();
        JobExecutionPlan plan = plan(gateway, condition(5));
        GatewayDecisionService service = service(decisions);

        GatewaySelection first = service.select(99L, gateway, plan, Map.of(), false);
        GatewaySelection recovered = service.select(99L, gateway, plan, Map.of(), false);

        assertEquals(java.util.Set.of(12L), first.selectedEdgeIds());
        assertFalse(first.reused());
        assertEquals(first.selectedEdgeIds(), recovered.selectedEdgeIds());
        assertTrue(recovered.reused());
        assertEquals(1, decisions.insertCount);
        assertEquals(GatewayDecisionStatus.SUCCESS, decisions.rows.getFirst().getStatus());
        assertEquals("12", decisions.rows.getFirst().getSelectedEdgeIds());
    }

    @Test
    void shouldPersistFailedDecisionWhenEvaluationFailsClosed() {
        RecordingDecisionMapper decisions = new RecordingDecisionMapper();
        JobExecutionNode gateway = gateway();
        GatewayDecisionService service = service(decisions);

        assertThrows(
                BusinessException.class,
                () -> service.select(99L, gateway, plan(gateway, condition("5")), Map.of(), false));

        assertEquals(1, decisions.insertCount);
        assertEquals(GatewayDecisionStatus.FAILED, decisions.rows.getFirst().getStatus());
        assertEquals(
                GatewayDecisionService.EVALUATION_ERROR_CODE,
                decisions.rows.getFirst().getErrorCode());
    }

    private GatewayDecisionService service(RecordingDecisionMapper decisions) {
        Instance instance = new Instance();
        instance.setId(99L);
        instance.setRootInstanceId(99L);
        instance.setTriggerId(77L);
        instance.setExecutionFence(0L);
        instance.setDefinitionId(10L);
        instance.setInputVariables("{\"amount\":5}");

        Trigger trigger = new Trigger();
        trigger.setId(77L);
        trigger.setTriggerType(TriggerType.MANUAL);
        trigger.setTriggerTime(LocalDateTime.of(2026, 9, 17, 1, 0));

        InstanceMapper instances =
                proxy(
                        InstanceMapper.class,
                        method -> "selectById".equals(method) ? instance : null);
        TriggerMapper triggers =
                proxy(TriggerMapper.class, method -> "selectById".equals(method) ? trigger : null);
        DagConditionValidator validator = new DagConditionValidator();
        return new GatewayDecisionService(
                decisions,
                instances,
                triggers,
                new VariableSnapshotService(objectMapper),
                new DagConditionEvaluator(validator),
                objectMapper);
    }

    private JobExecutionPlan plan(JobExecutionNode gateway, DagConditionExpression routeCondition) {
        JobExecutionNode selected = executionNode(3L);
        JobExecutionNode fallback = executionNode(4L);
        List<JobExecutionNode> nodes = List.of(gateway, selected, fallback);
        JobExecutionEdge selectedEdge =
                new JobExecutionEdge(12L, 10L, 2L, 3L, "selected", 10, false, 1, routeCondition);
        JobExecutionEdge defaultEdge =
                new JobExecutionEdge(13L, 10L, 2L, 4L, "default", 20, true, null, null);
        List<JobExecutionEdge> edges = List.of(selectedEdge, defaultEdge);
        return new JobExecutionPlan(
                10L,
                nodes,
                Map.of(2L, gateway, 3L, selected, 4L, fallback),
                Map.of(2L, List.of(), 3L, List.of(2L), 4L, List.of(2L)),
                Map.of(2L, List.of(3L, 4L), 3L, List.of(), 4L, List.of()),
                Map.of(2L, 0, 3L, 1, 4L, 1),
                List.of(gateway),
                edges,
                Map.of(12L, selectedEdge, 13L, defaultEdge),
                Map.of(2L, List.of(), 3L, List.of(selectedEdge), 4L, List.of(defaultEdge)),
                Map.of(2L, edges, 3L, List.of(), 4L, List.of()));
    }

    private JobExecutionNode gateway() {
        return new JobExecutionNode(
                2L,
                10L,
                "route",
                "Route",
                JobNodeKind.GATEWAY,
                GatewayMode.EXCLUSIVE,
                null,
                null,
                null,
                null,
                ExecuteCondition.ALWAYS,
                FailStrategy.STOP);
    }

    private JobExecutionNode executionNode(long id) {
        return new JobExecutionNode(
                id,
                10L,
                "node-" + id,
                "Node " + id,
                TargetType.TASK,
                100L + id,
                ExecuteCondition.ALL_SUCCESS,
                FailStrategy.STOP);
    }

    private DagConditionExpression condition(Object expected) {
        DagConditionOperand left = new DagConditionOperand();
        left.setVar("input.amount");
        DagConditionOperand right = new DagConditionOperand();
        right.setLiteral(expected);
        DagConditionExpression expression = new DagConditionExpression();
        expression.setOp(DagConditionOperator.EQ);
        expression.setLeft(left);
        expression.setRight(right);
        return expression;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.util.function.Function<String, Object> handler) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, arguments) -> handler.apply(method.getName()));
    }

    private static final class RecordingDecisionMapper implements DagGatewayDecisionMapper {
        private final List<DagGatewayDecision> rows = new ArrayList<>();
        private int insertCount;

        @Override
        public List<DagGatewayDecision> selectByJobInstanceId(Long jobInstanceId) {
            return List.copyOf(rows);
        }

        @Override
        public List<DagGatewayDecision> selectByJobInstanceIds(List<Long> jobInstanceIds) {
            return List.copyOf(rows);
        }

        @Override
        public List<DagGatewayDecision> selectByJobInstanceAndNode(
                Long jobInstanceId, Long definitionNodeId) {
            return List.copyOf(rows);
        }

        @Override
        public int insertIfOwned(DagGatewayDecision decision, Long rootInstanceId, Long triggerId) {
            insertCount++;
            rows.add(0, decision);
            return 1;
        }
    }
}
