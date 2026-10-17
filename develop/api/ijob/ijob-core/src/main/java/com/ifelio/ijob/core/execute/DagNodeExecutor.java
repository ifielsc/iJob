package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DagNodeExecutor {
    NodeExecuteResult execute(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext);

    NodeExecuteResult skip(
            JobExecutionNode node, Long parentInstanceId, Map<Long, Instance> nodeInstanceMap);

    default NodeExecuteResult execute(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext,
            List<JobExecutionNode> upstreamNodes) {
        return execute(node, parentInstanceId, nodeInstanceMap, controlContext);
    }

    /** Nested orchestration returns its completion without retaining a leaf worker. */
    default CompletableFuture<NodeExecuteResult> executeAsync(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext,
            List<JobExecutionNode> upstreamNodes) {
        return CompletableFuture.completedFuture(
                execute(node, parentInstanceId, nodeInstanceMap, controlContext, upstreamNodes));
    }

    default NodeExecuteResult skip(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            List<JobExecutionNode> upstreamNodes) {
        return skip(node, parentInstanceId, nodeInstanceMap);
    }

    default NodeExecuteResult skipBranch(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            List<JobExecutionNode> upstreamNodes) {
        return skip(node, parentInstanceId, nodeInstanceMap, upstreamNodes);
    }
}
