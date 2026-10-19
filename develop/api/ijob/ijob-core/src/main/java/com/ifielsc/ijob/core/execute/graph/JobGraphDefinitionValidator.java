package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ErrorDescriptor;
import com.ifielsc.ijob.common.response.MessageDefinition;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.job.JobEdgeRequest;
import com.ifielsc.ijob.contract.job.JobNodeRequest;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.condition.DagConditionValidator;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Component
public class JobGraphDefinitionValidator {
    public static final int MAX_NODE_COUNT = 500;
    public static final int MAX_EDGE_COUNT = 5000;
    public static final int MAX_NODE_CODE_LENGTH = 64;
    public static final int MAX_ROUTE_KEY_LENGTH = 64;
    public static final int MAX_ROUTE_PRIORITY = 10_000;

    private final DagConditionValidator conditionValidator = new DagConditionValidator();

    public void validate(List<JobNodeRequest> nodes, List<JobEdgeRequest> edges) {
        Map<String, JobNodeRequest> nodeMap = validateNodes(nodes);
        List<JobEdgeRequest> safeEdges = edges == null ? List.of() : edges;
        if (safeEdges.size() > MAX_EDGE_COUNT) {
            throw parameterError(CoreMessage.JOB_EDGE_COUNT_MUST_NOT_EXCEED_VALUE, MAX_EDGE_COUNT);
        }

        Map<String, List<String>> childMap = new HashMap<>();
        Map<String, Integer> indegreeMap = new HashMap<>();
        for (String nodeCode : nodeMap.keySet()) {
            childMap.put(nodeCode, new ArrayList<>());
            indegreeMap.put(nodeCode, 0);
        }

        Set<EdgeKey> edgeKeys = new HashSet<>();
        Set<String> connectedNodeCodes = new HashSet<>();
        Map<String, List<JobEdgeRequest>> outgoingEdges = new HashMap<>();
        Map<String, Set<String>> parentNodeCodes = new HashMap<>();
        for (JobEdgeRequest edge : safeEdges) {
            validateEdge(edge, nodeMap, edgeKeys, connectedNodeCodes, childMap, indegreeMap);
            outgoingEdges
                    .computeIfAbsent(edge.getFromNodeId(), ignored -> new ArrayList<>())
                    .add(edge);
            parentNodeCodes
                    .computeIfAbsent(edge.getToNodeId(), ignored -> new HashSet<>())
                    .add(edge.getFromNodeId());
        }

        validateRoutes(nodeMap, outgoingEdges, parentNodeCodes);
        validateIsolatedNodes(nodeMap, connectedNodeCodes);
        validateAcyclic(nodeMap, childMap, indegreeMap);
    }

    private void validateRoutes(
            Map<String, JobNodeRequest> nodeMap,
            Map<String, List<JobEdgeRequest>> outgoingEdges,
            Map<String, Set<String>> parentNodeCodes) {
        nodeMap.forEach(
                (nodeCode, node) -> {
                    List<JobEdgeRequest> outgoing = outgoingEdges.getOrDefault(nodeCode, List.of());
                    if (kindOf(node) == JobNodeKind.GATEWAY) {
                        validateGatewayRoutes(
                                nodeCode,
                                outgoing,
                                parentNodeCodes.getOrDefault(nodeCode, Set.of()));
                    } else {
                        outgoing.forEach(this::requirePlainEdge);
                    }
                });
    }

    private void validateGatewayRoutes(
            String nodeCode, List<JobEdgeRequest> edges, Set<String> parentNodeCodes) {
        if (edges.size() < 2) {
            throw new BusinessException(
                    new ErrorDescriptor(
                            ResultCode.PARAM_ERROR,
                            "IJOB-JOB-GATEWAY-ROUTES",
                            "error.job.gateway.routes",
                            "Gateway must have at least two outgoing routes"));
        }
        Set<String> routeKeys = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        int defaultCount = 0;
        for (JobEdgeRequest edge : edges) {
            String routeKey = edge.getRouteKey();
            if (routeKey == null
                    || routeKey.isBlank()
                    || !routeKey.equals(routeKey.trim())
                    || routeKey.length() > MAX_ROUTE_KEY_LENGTH
                    || !routeKeys.add(routeKey)) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR, "Gateway route key is invalid or duplicated");
            }
            Integer priority = edge.getPriority();
            if (priority == null
                    || priority < 0
                    || priority > MAX_ROUTE_PRIORITY
                    || !priorities.add(priority)) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR, "Gateway route priority is invalid or duplicated");
            }
            if (Boolean.TRUE.equals(edge.getDefaultRoute())) {
                defaultCount++;
                if (edge.getCondition() != null) {
                    throw BusinessException.localized(
                            ResultCode.PARAM_ERROR,
                            "Default gateway route cannot define a condition");
                }
            } else {
                if (edge.getCondition() == null) {
                    throw BusinessException.localized(
                            ResultCode.PARAM_ERROR,
                            "Non-default gateway route requires a condition");
                }
                conditionValidator.validate(edge.getCondition());
                validateConditionVariables(edge, parentNodeCodes);
            }
        }
        if (defaultCount != 1) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Gateway requires exactly one default route");
        }
    }

    private void validateConditionVariables(JobEdgeRequest edge, Set<String> parentNodeCodes) {
        for (String variable : conditionValidator.variables(edge.getCondition())) {
            if (!variable.startsWith("nodes.")) {
                continue;
            }
            long matches =
                    parentNodeCodes.stream()
                            .filter(parent -> variable.startsWith("nodes." + parent + "."))
                            .count();
            if (matches != 1) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR,
                        "Gateway condition may only reference a direct upstream node");
            }
        }
    }

    private void requirePlainEdge(JobEdgeRequest edge) {
        if (edge.getRouteKey() != null
                || edge.getPriority() != null
                || edge.getDefaultRoute() != null
                || edge.getCondition() != null) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR,
                    "Conditional route fields are only allowed on gateway outgoing edges");
        }
    }

    private JobNodeKind kindOf(JobNodeRequest node) {
        return node.getKind() == null ? JobNodeKind.EXECUTION : node.getKind();
    }

    private Map<String, JobNodeRequest> validateNodes(List<JobNodeRequest> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw parameterError(CoreMessage.A_JOB_REQUIRES_AT_LEAST_ONE_NODE);
        }
        if (nodes.size() > MAX_NODE_COUNT) {
            throw parameterError(CoreMessage.JOB_NODE_COUNT_MUST_NOT_EXCEED_VALUE, MAX_NODE_COUNT);
        }

        Map<String, JobNodeRequest> nodeMap = new LinkedHashMap<>();
        for (JobNodeRequest node : nodes) {
            if (node == null) {
                throw parameterError(CoreMessage.JOB_NODE_IS_REQUIRED);
            }

            String nodeCode = requireNodeCode(node.getNodeId());
            if (nodeMap.putIfAbsent(nodeCode, node) != null) {
                throw parameterError(CoreMessage.DUPLICATE_NODE_CODE_VALUE, nodeCode);
            }
        }
        return nodeMap;
    }

    private String requireNodeCode(String nodeCode) {
        if (nodeCode == null || nodeCode.isBlank()) {
            throw parameterError(CoreMessage.NODE_CODE_IS_REQUIRED);
        }
        if (!nodeCode.equals(nodeCode.trim())) {
            throw parameterError(
                    CoreMessage.NODE_CODE_MUST_NOT_HAVE_LEADING_OR_TRAILING_WHITESPACE);
        }
        if (nodeCode.length() > MAX_NODE_CODE_LENGTH) {
            throw parameterError(
                    CoreMessage.NODE_CODE_LENGTH_MUST_NOT_EXCEED_VALUE, MAX_NODE_CODE_LENGTH);
        }
        return nodeCode;
    }

    private void validateEdge(
            JobEdgeRequest edge,
            Map<String, JobNodeRequest> nodeMap,
            Set<EdgeKey> edgeKeys,
            Set<String> connectedNodeCodes,
            Map<String, List<String>> childMap,
            Map<String, Integer> indegreeMap) {
        if (edge == null) {
            throw parameterError(CoreMessage.JOB_EDGE_IS_REQUIRED);
        }

        String fromNodeCode = edge.getFromNodeId();
        String toNodeCode = edge.getToNodeId();
        if (fromNodeCode == null || fromNodeCode.isBlank()) {
            throw parameterError(CoreMessage.EDGE_SOURCE_IS_REQUIRED);
        }
        if (toNodeCode == null || toNodeCode.isBlank()) {
            throw parameterError(CoreMessage.EDGE_TARGET_IS_REQUIRED);
        }
        if (!nodeMap.containsKey(fromNodeCode)) {
            throw parameterError(CoreMessage.EDGE_SOURCE_DOES_NOT_EXIST_VALUE, fromNodeCode);
        }
        if (!nodeMap.containsKey(toNodeCode)) {
            throw parameterError(CoreMessage.EDGE_TARGET_DOES_NOT_EXIST_VALUE, toNodeCode);
        }
        if (fromNodeCode.equals(toNodeCode)) {
            throw parameterError(
                    CoreMessage.JOB_EDGE_MUST_NOT_REFERENCE_ITSELF_VALUE, fromNodeCode);
        }

        EdgeKey edgeKey = new EdgeKey(fromNodeCode, toNodeCode);
        if (!edgeKeys.add(edgeKey)) {
            throw parameterError(
                    CoreMessage.DUPLICATE_JOB_EDGE_VALUE_VALUE, fromNodeCode, toNodeCode);
        }

        connectedNodeCodes.add(fromNodeCode);
        connectedNodeCodes.add(toNodeCode);
        childMap.get(fromNodeCode).add(toNodeCode);
        indegreeMap.compute(toNodeCode, (key, value) -> value + 1);
    }

    private void validateIsolatedNodes(
            Map<String, JobNodeRequest> nodeMap, Set<String> connectedNodeCodes) {
        if (nodeMap.size() == 1) {
            return;
        }

        for (String nodeCode : nodeMap.keySet()) {
            if (!connectedNodeCodes.contains(nodeCode)) {
                throw parameterError(
                        CoreMessage.MULTI_NODE_JOB_MUST_NOT_CONTAIN_AN_ISOLATED_NODE_VALUE,
                        nodeCode);
            }
        }
    }

    private void validateAcyclic(
            Map<String, JobNodeRequest> nodeMap,
            Map<String, List<String>> childMap,
            Map<String, Integer> indegreeMap) {
        Map<String, Integer> remainingIndegree = new HashMap<>(indegreeMap);
        Queue<String> readyNodes = new ArrayDeque<>();
        remainingIndegree.forEach(
                (nodeCode, indegree) -> {
                    if (indegree == 0) {
                        readyNodes.offer(nodeCode);
                    }
                });

        int visitedNodeCount = 0;
        while (!readyNodes.isEmpty()) {
            String nodeCode = readyNodes.poll();
            visitedNodeCount++;
            for (String childNodeCode : childMap.get(nodeCode)) {
                int remaining = remainingIndegree.compute(childNodeCode, (key, value) -> value - 1);
                if (remaining == 0) {
                    readyNodes.offer(childNodeCode);
                }
            }
        }

        if (visitedNodeCount != nodeMap.size()) {
            throw parameterError(CoreMessage.JOB_GRAPH_MUST_NOT_CONTAIN_CYCLES);
        }
    }

    private BusinessException parameterError(MessageDefinition message, Object... arguments) {
        return new BusinessException(ResultCode.PARAM_ERROR, message, arguments);
    }

    private record EdgeKey(String fromNodeCode, String toNodeCode) {}
}
