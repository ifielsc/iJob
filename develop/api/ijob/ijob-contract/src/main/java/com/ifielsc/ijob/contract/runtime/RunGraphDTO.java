package com.ifielsc.ijob.contract.runtime;

import com.ifielsc.ijob.contract.job.JobLayout;

import java.util.List;

/** Safe execution snapshot: no variables, scripts, conditions or connection settings. */
public record RunGraphDTO(
        String instanceId,
        String rootInstanceId,
        String jobId,
        String definitionId,
        String name,
        String status,
        JobLayout layout,
        List<Node> nodes,
        List<Edge> edges) {
    public record Node(
            String id,
            String nodeKey,
            String name,
            String kind,
            String nodeType,
            String refId,
            String instanceId,
            String status,
            Integer progress,
            Integer retryCount) {}

    public record Edge(
            String id,
            String fromNodeId,
            String toNodeId,
            String routeKey,
            Integer priority,
            boolean defaultRoute,
            boolean selected) {}
}
