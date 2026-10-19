package com.ifielsc.ijob.core.execute.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JobExecutionPlan(
        Long jobDefinitionId,
        List<JobExecutionNode> nodes,
        Map<Long, JobExecutionNode> nodeMap,
        Map<Long, List<Long>> parentMap,
        Map<Long, List<Long>> childMap,
        Map<Long, Integer> indegreeMap,
        List<JobExecutionNode> startNodes,
        List<JobExecutionEdge> edges,
        Map<Long, JobExecutionEdge> edgeMap,
        Map<Long, List<JobExecutionEdge>> incomingEdgeMap,
        Map<Long, List<JobExecutionEdge>> outgoingEdgeMap) {
    public JobExecutionPlan(
            Long jobDefinitionId,
            List<JobExecutionNode> nodes,
            Map<Long, JobExecutionNode> nodeMap,
            Map<Long, List<Long>> parentMap,
            Map<Long, List<Long>> childMap,
            Map<Long, Integer> indegreeMap,
            List<JobExecutionNode> startNodes) {
        this(
                jobDefinitionId,
                nodes,
                nodeMap,
                parentMap,
                childMap,
                indegreeMap,
                startNodes,
                List.of(),
                Map.of(),
                emptyEdgeMap(nodes),
                emptyEdgeMap(nodes));
    }

    public JobExecutionPlan {
        nodes = List.copyOf(nodes);
        nodeMap = Map.copyOf(nodeMap);
        parentMap = immutableRelationMap(parentMap);
        childMap = immutableRelationMap(childMap);
        indegreeMap = Map.copyOf(indegreeMap);
        startNodes = List.copyOf(startNodes);
        edges = List.copyOf(edges);
        edgeMap = Map.copyOf(edgeMap);
        incomingEdgeMap = immutableEdgeRelationMap(incomingEdgeMap);
        outgoingEdgeMap = immutableEdgeRelationMap(outgoingEdgeMap);
    }

    private static Map<Long, List<Long>> immutableRelationMap(Map<Long, List<Long>> source) {
        Map<Long, List<Long>> copy = new LinkedHashMap<>();
        source.forEach((nodeId, relations) -> copy.put(nodeId, List.copyOf(relations)));
        return Map.copyOf(copy);
    }

    private static Map<Long, List<JobExecutionEdge>> immutableEdgeRelationMap(
            Map<Long, List<JobExecutionEdge>> source) {
        Map<Long, List<JobExecutionEdge>> copy = new LinkedHashMap<>();
        source.forEach((nodeId, relations) -> copy.put(nodeId, List.copyOf(relations)));
        return Map.copyOf(copy);
    }

    private static Map<Long, List<JobExecutionEdge>> emptyEdgeMap(List<JobExecutionNode> nodes) {
        Map<Long, List<JobExecutionEdge>> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(node.id(), List.of()));
        return result;
    }
}
