package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Component
public class JobRetryPlanner {
    public JobRetryPlan plan(
            InstanceStatus rootStatus,
            JobExecutionPlan executionPlan,
            List<Instance> nodeInstances) {
        return plan(rootStatus, executionPlan, nodeInstances, Set.of());
    }

    public JobRetryPlan plan(
            InstanceStatus rootStatus,
            JobExecutionPlan executionPlan,
            List<Instance> nodeInstances,
            Set<Long> failedGatewayNodeIds) {
        Set<Long> retryNodeIds =
                rootStatus == InstanceStatus.TIMED_OUT
                        ? allNodeIds(executionPlan)
                        : failedNodeIds(executionPlan, nodeInstances);
        if (rootStatus != InstanceStatus.TIMED_OUT && failedGatewayNodeIds != null) {
            for (Long gatewayNodeId : failedGatewayNodeIds) {
                JobExecutionNode node = executionPlan.nodeMap().get(gatewayNodeId);
                if (node == null
                        || node.kind() != com.ifelio.ijob.contract.enums.JobNodeKind.GATEWAY) {
                    throw BusinessException.localized(
                            ResultCode.SYSTEM_ERROR, "Gateway retry snapshot is invalid");
                }
                retryNodeIds.add(gatewayNodeId);
            }
        }
        if (retryNodeIds.isEmpty()) {
            throw BusinessException.localized(ResultCode.CONFLICT, "No failed node");
        }

        Set<Long> retryClosure = descendantClosure(executionPlan, retryNodeIds);
        List<Long> resetInstanceIds = new ArrayList<>();
        for (Instance nodeInstance : nodeInstances) {
            if (retryClosure.contains(nodeInstance.getDefinitionNodeId())) {
                resetInstanceIds.add(nodeInstance.getId());
            }
        }
        return new JobRetryPlan(retryClosure, resetInstanceIds);
    }

    public JobRetryPlan planWhole(JobExecutionPlan executionPlan, List<Instance> nodeInstances) {
        Set<Long> retryNodeIds = allNodeIds(executionPlan);
        List<Long> resetInstanceIds = new ArrayList<>();
        for (Instance nodeInstance : nodeInstances) {
            Long nodeId = nodeInstance.getDefinitionNodeId();
            if (nodeId == null || !executionPlan.nodeMap().containsKey(nodeId)) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Job node retry snapshot is invalid");
            }
            resetInstanceIds.add(nodeInstance.getId());
        }
        return new JobRetryPlan(retryNodeIds, resetInstanceIds);
    }

    private Set<Long> allNodeIds(JobExecutionPlan executionPlan) {
        Set<Long> nodeIds = new LinkedHashSet<>();
        for (JobExecutionNode node : executionPlan.nodes()) {
            nodeIds.add(node.id());
        }
        return nodeIds;
    }

    private Set<Long> failedNodeIds(JobExecutionPlan executionPlan, List<Instance> nodeInstances) {
        Set<Long> failedNodeIds = new LinkedHashSet<>();
        for (Instance nodeInstance : nodeInstances) {
            if (!nodeInstance.getStatus().isRetryable()
                    && nodeInstance.getStatus() != InstanceStatus.STOPPED) {
                continue;
            }
            Long nodeId = nodeInstance.getDefinitionNodeId();
            if (nodeId == null || !executionPlan.nodeMap().containsKey(nodeId)) {
                throw BusinessException.localized(
                        ResultCode.SYSTEM_ERROR, "Job node retry snapshot is invalid");
            }
            failedNodeIds.add(nodeId);
        }
        return failedNodeIds;
    }

    private Set<Long> descendantClosure(JobExecutionPlan executionPlan, Set<Long> failedNodeIds) {
        Set<Long> collected = new HashSet<>(failedNodeIds);
        Queue<Long> queue = new ArrayDeque<>(failedNodeIds);
        while (!queue.isEmpty()) {
            Long current = queue.remove();
            for (Long childId : executionPlan.childMap().getOrDefault(current, List.of())) {
                if (collected.add(childId)) {
                    queue.offer(childId);
                }
            }
        }

        Set<Long> ordered = new LinkedHashSet<>();
        for (JobExecutionNode node : executionPlan.nodes()) {
            if (collected.contains(node.id())) {
                ordered.add(node.id());
            }
        }
        return ordered;
    }
}
