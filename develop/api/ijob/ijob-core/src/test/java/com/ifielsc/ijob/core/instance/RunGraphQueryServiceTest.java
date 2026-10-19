// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.GatewayDecisionStatus;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.job.JobDefinition;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.runtime.DagGatewayDecision;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.runtime.DagGatewayDecisionMapper;
import com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

class RunGraphQueryServiceTest {
    @Test
    void completeDefinitionIncludesUnmaterializedNodesAndDoesNotMixRepeatedReferences() {
        var f = new Fixture();
        var graph = f.service().get(1L);
        assertEquals(3, graph.nodes().size());
        assertEquals("SUCCESS", graph.nodes().get(0).status());
        assertEquals("NOT_STARTED", graph.nodes().get(1).status());
        assertEquals("9007199254740993", graph.nodes().get(0).instanceId());
        assertEquals("Historical", graph.name());
    }

    @Test
    void terminalRootDoesNotInventSkippedOrSuccessForNodesWithoutInstances() {
        var f = new Fixture();
        f.root.setStatus(InstanceStatus.STOPPED);
        assertEquals("NOT_EXECUTED", f.service().get(1L).nodes().get(1).status());
    }

    @Test
    void gatewayUsesLatestEvaluationAndExactSelectedEdge() {
        var f = new Fixture();
        var a = f.decision(5L, 1, GatewayDecisionStatus.FAILED, "[]");
        var b = f.decision(6L, 2, GatewayDecisionStatus.SUCCESS, "[9007199254740995]");
        f.decisions = List.of(b, a);
        var graph = f.service().get(1L);
        assertEquals("SUCCESS", graph.nodes().get(2).status());
        assertTrue(graph.edges().getFirst().selected());
    }

    @Test
    void gatewayReadsTheScalarIdWrittenByExclusiveDecisionServiceWithoutPrecisionLoss() {
        var f = new Fixture();
        f.decisions = List.of(f.decision(6L, 1, GatewayDecisionStatus.SUCCESS, "9007199254740995"));
        var graph = f.service().get(1L);
        assertEquals("SUCCESS", graph.nodes().get(2).status());
        assertTrue(graph.edges().getFirst().selected());
        assertEquals("9007199254740995", graph.edges().getFirst().id());
    }

    @Test
    void missingOrMismatchedDefinitionFailsInsteadOfUsingCurrentJob() {
        var f = new Fixture();
        f.definition.setJobId(123L);
        assertThrows(BusinessException.class, () -> f.service().get(1L));
        assertThrows(BusinessException.class, () -> f.service().get(0L));
    }

    static class Fixture {
        Instance root = new Instance(), child = new Instance();
        JobDefinition definition = new JobDefinition();
        List<DagGatewayDecision> decisions = List.of();

        Fixture() {
            root.setId(1L);
            root.setRootInstanceId(1L);
            root.setTargetType(TargetType.JOB);
            root.setTargetId(7L);
            root.setDefinitionId(8L);
            root.setStatus(InstanceStatus.RUNNING);
            definition.setId(8L);
            definition.setJobId(7L);
            definition.setName("Historical");
            child.setId(9007199254740993L);
            child.setParentInstanceId(1L);
            child.setDefinitionNodeId(10L);
            child.setStatus(InstanceStatus.SUCCESS);
        }

        DagGatewayDecision decision(
                long id, int evaluation, GatewayDecisionStatus status, String edges) {
            var d = new DagGatewayDecision();
            d.setId(id);
            d.setJobInstanceId(1L);
            d.setDefinitionNodeId(12L);
            d.setEvaluationNo(evaluation);
            d.setStatus(status);
            d.setSelectedEdgeIds(edges);
            return d;
        }

        JobDefinitionNode node(long id, JobNodeKind kind) {
            var n = new JobDefinitionNode();
            n.setId(id);
            n.setNodeKey("node" + id);
            n.setName("Node");
            n.setNodeKind(kind);
            n.setNodeType(TargetType.TASK);
            n.setRefId(100L);
            return n;
        }

        RunGraphQueryService service() {
            var e = new JobDefinitionEdge();
            e.setId(9007199254740995L);
            e.setFromNodeId(12L);
            e.setToNodeId(11L);
            return new RunGraphQueryService(
                    stub(
                            InstanceMapper.class,
                            Map.of(
                                    "selectSummaryById",
                                    root,
                                    "selectByParentInstanceId",
                                    List.of(child))),
                    stub(JobDefinitionMapper.class, Map.of("selectById", definition)),
                    stub(
                            JobDefinitionNodeMapper.class,
                            Map.of(
                                    "selectListByDefinitionId",
                                    List.of(
                                            node(10L, JobNodeKind.EXECUTION),
                                            node(11L, JobNodeKind.EXECUTION),
                                            node(12L, JobNodeKind.GATEWAY)))),
                    stub(
                            JobDefinitionEdgeMapper.class,
                            Map.of("selectListByDefinitionId", List.of(e))),
                    stub(
                            DagGatewayDecisionMapper.class,
                            Map.of("selectByJobInstanceId", decisions)),
                    new ObjectMapper());
        }
    }

    static <T> T stub(Class<T> type, Map<String, Object> values) {
        return type.cast(
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, a) -> {
                            if (!values.containsKey(m.getName()))
                                throw new UnsupportedOperationException(m.getName());
                            return values.get(m.getName());
                        }));
    }
}
