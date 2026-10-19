package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.core.execute.condition.DagConditionCodec;
import com.ifielsc.ijob.core.execute.condition.DagConditionValidator;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionEdge;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNode;
import com.ifielsc.ijob.infra.entity.job.JobDefinitionNodePolicy;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionEdgeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodeMapper;
import com.ifielsc.ijob.infra.mapper.job.JobDefinitionNodePolicyMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobExecutionPlanBuilder {
    private final JobDefinitionNodeMapper nodeMapper;
    private final JobDefinitionEdgeMapper edgeMapper;
    private final JobDefinitionNodePolicyMapper policyMapper;
    private final DagConditionValidator conditionValidator = new DagConditionValidator();

    public JobExecutionPlan build(Long definitionId) {
        if (definitionId == null || definitionId <= 0) {
            throw systemError("Job definition snapshot is missing");
        }
        List<JobDefinitionNode> nodes = nodeMapper.selectListByDefinitionId(definitionId);
        List<JobDefinitionEdge> edges = edgeMapper.selectListByDefinitionId(definitionId);
        List<JobDefinitionNodePolicy> policies =
                policyMapper.selectListByDefinitionId(definitionId);
        validateRows(definitionId, nodes, edges, policies);

        Map<Long, JobDefinitionNodePolicy> policyMap =
                policies.stream()
                        .collect(
                                Collectors.toMap(
                                        JobDefinitionNodePolicy::getNodeId, Function.identity()));
        List<JobExecutionNode> executionNodes =
                nodes.stream()
                        .map(node -> toExecutionNode(node, policyMap.get(node.getId())))
                        .toList();
        Map<Long, JobExecutionNode> nodeMap =
                executionNodes.stream()
                        .collect(
                                Collectors.toMap(
                                        JobExecutionNode::id,
                                        Function.identity(),
                                        (first, second) -> first,
                                        LinkedHashMap::new));

        Map<Long, List<Long>> parentMap = new LinkedHashMap<>();
        Map<Long, List<Long>> childMap = new LinkedHashMap<>();
        buildRelationMap(executionNodes, edges, parentMap, childMap);
        Map<Long, Integer> indegreeMap = buildIndegreeMap(executionNodes, parentMap);
        List<JobExecutionNode> startNodes = buildStartNodes(executionNodes, indegreeMap);
        validateAcyclic(executionNodes, childMap, indegreeMap);
        List<JobExecutionEdge> executionEdges = edges.stream().map(this::toExecutionEdge).toList();
        Map<Long, JobExecutionEdge> executionEdgeMap =
                executionEdges.stream()
                        .collect(Collectors.toMap(JobExecutionEdge::id, Function.identity()));
        Map<Long, List<JobExecutionEdge>> incomingEdgeMap = edgeRelationMap(executionNodes);
        Map<Long, List<JobExecutionEdge>> outgoingEdgeMap = edgeRelationMap(executionNodes);
        executionEdges.forEach(
                edge -> {
                    outgoingEdgeMap.get(edge.fromNodeId()).add(edge);
                    incomingEdgeMap.get(edge.toNodeId()).add(edge);
                });

        return new JobExecutionPlan(
                definitionId,
                executionNodes,
                nodeMap,
                parentMap,
                childMap,
                indegreeMap,
                startNodes,
                executionEdges,
                executionEdgeMap,
                incomingEdgeMap,
                outgoingEdgeMap);
    }

    private JobExecutionNode toExecutionNode(
            JobDefinitionNode node, JobDefinitionNodePolicy policy) {
        return new JobExecutionNode(
                node.getId(),
                node.getDefinitionId(),
                node.getNodeKey(),
                node.getName(),
                kindOf(node),
                node.getGatewayMode(),
                node.getNodeType(),
                node.getRefId(),
                node.getRefDefinitionId(),
                node.getRefTaskDefinitionId(),
                policy.getExecuteCondition(),
                policy.getFailStrategy());
    }

    private JobExecutionEdge toExecutionEdge(JobDefinitionEdge edge) {
        return new JobExecutionEdge(
                edge.getId(),
                edge.getDefinitionId(),
                edge.getFromNodeId(),
                edge.getToNodeId(),
                edge.getRouteKey(),
                edge.getRoutePriority(),
                Boolean.TRUE.equals(edge.getDefaultRoute()),
                edge.getConditionVersion(),
                DagConditionCodec.decode(edge.getConditionVersion(), edge.getConditionPayload()));
    }

    private Map<Long, List<JobExecutionEdge>> edgeRelationMap(List<JobExecutionNode> nodes) {
        Map<Long, List<JobExecutionEdge>> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(node.id(), new ArrayList<>()));
        return result;
    }

    private void buildRelationMap(
            List<JobExecutionNode> nodes,
            List<JobDefinitionEdge> edges,
            Map<Long, List<Long>> parentMap,
            Map<Long, List<Long>> childMap) {
        for (JobExecutionNode node : nodes) {
            parentMap.put(node.id(), new ArrayList<>());
            childMap.put(node.id(), new ArrayList<>());
        }
        for (JobDefinitionEdge edge : edges) {
            parentMap.get(edge.getToNodeId()).add(edge.getFromNodeId());
            childMap.get(edge.getFromNodeId()).add(edge.getToNodeId());
        }
    }

    private Map<Long, Integer> buildIndegreeMap(
            List<JobExecutionNode> nodes, Map<Long, List<Long>> parentMap) {
        Map<Long, Integer> indegreeMap = new LinkedHashMap<>();
        for (JobExecutionNode node : nodes) {
            indegreeMap.put(node.id(), parentMap.get(node.id()).size());
        }
        return indegreeMap;
    }

    private List<JobExecutionNode> buildStartNodes(
            List<JobExecutionNode> nodes, Map<Long, Integer> indegreeMap) {
        return nodes.stream().filter(node -> indegreeMap.get(node.id()) == 0).toList();
    }

    private void validateAcyclic(
            List<JobExecutionNode> nodes,
            Map<Long, List<Long>> childMap,
            Map<Long, Integer> indegreeMap) {
        Map<Long, Integer> remainingIndegree = new HashMap<>(indegreeMap);
        Queue<Long> readyNodes = new ArrayDeque<>();
        remainingIndegree.forEach(
                (nodeId, indegree) -> {
                    if (indegree == 0) {
                        readyNodes.offer(nodeId);
                    }
                });

        int visitedNodeCount = 0;
        while (!readyNodes.isEmpty()) {
            Long nodeId = readyNodes.poll();
            visitedNodeCount++;
            for (Long childId : childMap.get(nodeId)) {
                int remaining = remainingIndegree.compute(childId, (key, value) -> value - 1);
                if (remaining == 0) {
                    readyNodes.offer(childId);
                }
            }
        }
        if (visitedNodeCount != nodes.size()) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Job graph contains cycle");
        }
    }

    private void validateRows(
            Long definitionId,
            List<JobDefinitionNode> nodes,
            List<JobDefinitionEdge> edges,
            List<JobDefinitionNodePolicy> policies) {
        if (nodes == null
                || nodes.isEmpty()
                || edges == null
                || policies == null
                || policies.size() != nodes.size()) {
            throw systemError("Job definition graph is incomplete");
        }
        Set<Long> nodeIds = new HashSet<>();
        for (JobDefinitionNode node : nodes) {
            if (node == null
                    || node.getId() == null
                    || !nodeIds.add(node.getId())
                    || !definitionId.equals(node.getDefinitionId())
                    || !validNode(node)) {
                throw systemError("Job definition node is invalid");
            }
        }
        Set<Long> policyNodes = new HashSet<>();
        for (JobDefinitionNodePolicy policy : policies) {
            if (policy == null
                    || !definitionId.equals(policy.getDefinitionId())
                    || !nodeIds.contains(policy.getNodeId())
                    || !policyNodes.add(policy.getNodeId())
                    || policy.getExecuteCondition() == null
                    || policy.getFailStrategy() == null) {
                throw systemError("Job definition policy is invalid");
            }
        }
        for (JobDefinitionEdge edge : edges) {
            if (edge == null
                    || edge.getId() == null
                    || edge.getId() <= 0
                    || !definitionId.equals(edge.getDefinitionId())
                    || !nodeIds.contains(edge.getFromNodeId())
                    || !nodeIds.contains(edge.getToNodeId())
                    || edge.getFromNodeId().equals(edge.getToNodeId())) {
                throw systemError("Job definition edge is invalid");
            }
            validateEdge(nodeById(nodes, edge.getFromNodeId()), edge);
        }
    }

    private boolean validNode(JobDefinitionNode node) {
        if (kindOf(node) == JobNodeKind.GATEWAY) {
            return node.getGatewayMode() != null
                    && node.getNodeType() == null
                    && node.getRefId() == null
                    && node.getRefDefinitionId() == null
                    && node.getRefTaskDefinitionId() == null;
        }
        return node.getGatewayMode() == null
                && node.getNodeType() != null
                && node.getRefId() != null
                && node.getRefId() > 0
                && validSnapshot(node);
    }

    private void validateEdge(JobDefinitionNode source, JobDefinitionEdge edge) {
        if (kindOf(source) != JobNodeKind.GATEWAY) {
            if (edge.getRouteKey() != null
                    || edge.getRoutePriority() != null
                    || Boolean.TRUE.equals(edge.getDefaultRoute())
                    || edge.getConditionVersion() != null
                    || edge.getConditionPayload() != null) {
                throw systemError("Ordinary job definition edge contains route metadata");
            }
            return;
        }
        if (edge.getRouteKey() == null || edge.getRoutePriority() == null) {
            throw systemError("Gateway route metadata is incomplete");
        }
        if (Boolean.TRUE.equals(edge.getDefaultRoute())) {
            if (edge.getConditionVersion() != null || edge.getConditionPayload() != null) {
                throw systemError("Default gateway route contains a condition");
            }
        } else {
            conditionValidator.validate(
                    DagConditionCodec.decode(
                            edge.getConditionVersion(), edge.getConditionPayload()));
        }
    }

    private JobDefinitionNode nodeById(List<JobDefinitionNode> nodes, Long nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> systemError("Job definition edge source is missing"));
    }

    private JobNodeKind kindOf(JobDefinitionNode node) {
        return node.getNodeKind() == null ? JobNodeKind.EXECUTION : node.getNodeKind();
    }

    private boolean validSnapshot(JobDefinitionNode node) {
        if (node.getNodeType() == com.ifielsc.ijob.contract.enums.TargetType.TASK) {
            return node.getRefDefinitionId() == null
                    && node.getRefTaskDefinitionId() != null
                    && node.getRefTaskDefinitionId() > 0;
        }
        return node.getRefDefinitionId() != null
                && node.getRefDefinitionId() > 0
                && node.getRefTaskDefinitionId() == null;
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }
}
