package com.ifielsc.ijob.core.execute.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

class JobExecutionPlanBuilderTest {
    @Test
    void buildShouldCreateImmutablePlanForValidDag() {
        JobDefinitionNode first = node(1L);
        JobDefinitionNode second = node(2L);
        JobDefinitionNode third = node(3L);
        JobExecutionPlanBuilder builder =
                builder(
                        List.of(first, second, third),
                        List.of(edge(1L, 2L), edge(1L, 3L)),
                        List.of(policy(1L), policy(2L), policy(3L)));

        JobExecutionPlan plan = builder.build(10L);

        assertEquals(10L, plan.jobDefinitionId());
        assertEquals(List.of(1L), plan.parentMap().get(2L));
        assertEquals(List.of(2L, 3L), plan.childMap().get(1L));
        assertEquals(0, plan.indegreeMap().get(1L));
        assertEquals(List.of(1L), plan.startNodes().stream().map(JobExecutionNode::id).toList());
        assertEquals(102L, plan.nodeMap().get(2L).refId());
        assertEquals(FailStrategy.STOP, plan.nodeMap().get(1L).failStrategy());

        first.setRefId(999L);
        assertEquals(101L, plan.nodeMap().get(1L).refId());
        assertThrows(UnsupportedOperationException.class, () -> plan.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.childMap().get(1L).add(4L));
    }

    @Test
    void buildShouldRejectGraphContainingCycle() {
        JobExecutionPlanBuilder builder =
                builder(
                        List.of(node(1L), node(2L)),
                        List.of(edge(1L, 2L), edge(2L, 1L)),
                        List.of(policy(1L), policy(2L)));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> builder.build(10L));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
        assertEquals("Job graph contains cycle", exception.getMessage());
    }

    private static JobExecutionPlanBuilder builder(
            List<JobDefinitionNode> nodes,
            List<JobDefinitionEdge> edges,
            List<JobDefinitionNodePolicy> policies) {
        return new JobExecutionPlanBuilder(
                new StubNodeMapper(nodes),
                new StubEdgeMapper(edges),
                new StubPolicyMapper(policies));
    }

    private static JobDefinitionNode node(Long id) {
        JobDefinitionNode node = new JobDefinitionNode();
        node.setId(id);
        node.setDefinitionId(10L);
        node.setNodeKey("node-" + id);
        node.setName("Node " + id);
        node.setNodeType(TargetType.TASK);
        node.setRefId(100L + id);
        node.setRefTaskDefinitionId(200L + id);
        return node;
    }

    private static JobDefinitionEdge edge(Long fromNodeId, Long toNodeId) {
        JobDefinitionEdge edge = new JobDefinitionEdge();
        edge.setId(fromNodeId * 100 + toNodeId);
        edge.setDefinitionId(10L);
        edge.setFromNodeId(fromNodeId);
        edge.setToNodeId(toNodeId);
        return edge;
    }

    private static JobDefinitionNodePolicy policy(Long nodeId) {
        JobDefinitionNodePolicy policy = new JobDefinitionNodePolicy();
        policy.setDefinitionId(10L);
        policy.setNodeId(nodeId);
        policy.setExecuteCondition(ExecuteCondition.ALL_SUCCESS);
        policy.setFailStrategy(FailStrategy.STOP);
        return policy;
    }

    private record StubNodeMapper(List<JobDefinitionNode> nodes)
            implements JobDefinitionNodeMapper {
        @Override
        public List<JobDefinitionNode> selectListByDefinitionId(Long definitionId) {
            return nodes;
        }

        @Override
        public int insert(JobDefinitionNode node) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubEdgeMapper(List<JobDefinitionEdge> edges)
            implements JobDefinitionEdgeMapper {
        @Override
        public List<JobDefinitionEdge> selectListByDefinitionId(Long definitionId) {
            return edges;
        }

        @Override
        public int insert(JobDefinitionEdge edge) {
            throw new UnsupportedOperationException();
        }
    }

    private record StubPolicyMapper(List<JobDefinitionNodePolicy> policies)
            implements JobDefinitionNodePolicyMapper {
        @Override
        public List<JobDefinitionNodePolicy> selectListByDefinitionId(Long definitionId) {
            return policies;
        }

        @Override
        public int insert(JobDefinitionNodePolicy policy) {
            throw new UnsupportedOperationException();
        }
    }
}
