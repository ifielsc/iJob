// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class JobNodeExecutor implements DagNodeExecutor {
    private final InstanceService instanceService;
    private final TaskExecuteService taskExecuteService;
    private final JobExecuteService jobExecuteService;
    private final VariableSnapshotService variableSnapshotService;

    public JobNodeExecutor(
            InstanceService instanceService,
            TaskExecuteService taskExecuteService,
            @Lazy JobExecuteService jobExecuteService,
            VariableSnapshotService variableSnapshotService) {
        this.instanceService = instanceService;
        this.taskExecuteService = taskExecuteService;
        this.jobExecuteService = jobExecuteService;
        this.variableSnapshotService = variableSnapshotService;
    }

    @Override
    public NodeExecuteResult execute(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext) {
        return execute(node, parentInstanceId, nodeInstanceMap, controlContext, List.of());
    }

    @Override
    public NodeExecuteResult execute(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext,
            List<JobExecutionNode> upstreamNodes) {
        controlContext.checkStop();
        controlContext.checkPause();
        controlContext.checkInterrupted();

        Instance child = null;
        try {
            child = getOrCreateNodeInstance(parentInstanceId, node, nodeInstanceMap, upstreamNodes);
            Long childInstanceId = child.getId();
            switch (node.nodeType()) {
                case TASK -> taskExecuteService.execute(childInstanceId);
                case JOB -> jobExecuteService.execute(childInstanceId);
            }
            return resultFromLatest(node, childInstanceId, nodeInstanceMap);
        } catch (StopException exception) {
            if (controlContext.getHandle().isStopRequested()) {
                throw exception;
            }
            stopChildIfActive(child);
            return NodeExecuteResult.of(node, NodeExecuteStatus.STOPPED);
        } catch (Exception exception) {
            failChildIfActive(child);
            return NodeExecuteResult.failed(node, exception);
        }
    }

    @Override
    public CompletableFuture<NodeExecuteResult> executeAsync(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            TaskContext controlContext,
            List<JobExecutionNode> upstreamNodes) {
        if (node.nodeType() != TargetType.JOB) {
            return DagNodeExecutor.super.executeAsync(
                    node, parentInstanceId, nodeInstanceMap, controlContext, upstreamNodes);
        }
        controlContext.checkExecution();
        Instance child =
                getOrCreateNodeInstance(parentInstanceId, node, nodeInstanceMap, upstreamNodes);
        CompletableFuture<Void> execution;
        try {
            execution = jobExecuteService.executeAsync(child.getId());
        } catch (RuntimeException failure) {
            failChildIfActive(child);
            return CompletableFuture.completedFuture(NodeExecuteResult.failed(node, failure));
        }
        var result =
                execution.handle(
                        (ignored, failure) -> {
                            if (failure != null) {
                                Throwable cause =
                                        failure instanceof CompletionException
                                                ? failure.getCause()
                                                : failure;
                                if (cause instanceof StopException) {
                                    if (controlContext.getHandle().isStopRequested())
                                        throw new StopException();
                                    stopChildIfActive(child);
                                    return NodeExecuteResult.of(node, NodeExecuteStatus.STOPPED);
                                }
                                failChildIfActive(child);
                                return NodeExecuteResult.failed(node, cause);
                            }
                            return resultFromLatest(node, child.getId(), nodeInstanceMap);
                        });
        result.whenComplete(
                (ignored, failure) -> {
                    if (result.isCancelled()) execution.cancel(true);
                });
        return result;
    }

    @Override
    public NodeExecuteResult skip(
            JobExecutionNode node, Long parentInstanceId, Map<Long, Instance> nodeInstanceMap) {
        return skip(node, parentInstanceId, nodeInstanceMap, List.of());
    }

    @Override
    public NodeExecuteResult skip(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            List<JobExecutionNode> upstreamNodes) {
        Instance child =
                getOrCreateNodeInstance(parentInstanceId, node, nodeInstanceMap, upstreamNodes);
        Instance latest = instanceService.getInstance(child.getId());
        if (!latest.getStatus().isFinished()) {
            instanceService.skipped(child.getId());
            latest = instanceService.getInstance(child.getId());
        }
        nodeInstanceMap.put(node.id(), latest);
        return resultFromStatus(node, latest.getStatus());
    }

    @Override
    public NodeExecuteResult skipBranch(
            JobExecutionNode node,
            Long parentInstanceId,
            Map<Long, Instance> nodeInstanceMap,
            List<JobExecutionNode> upstreamNodes) {
        Instance child =
                getOrCreateNodeInstance(parentInstanceId, node, nodeInstanceMap, upstreamNodes);
        Instance latest = instanceService.getInstance(child.getId());
        if (!latest.getStatus().isFinished()) {
            instanceService.completeTask(
                    child.getId(),
                    InstanceStatus.SKIPPED,
                    "Branch not selected",
                    RuntimeErrorMetadata.branchNotSelected());
            latest = instanceService.getInstance(child.getId());
        }
        nodeInstanceMap.put(node.id(), latest);
        return NodeExecuteResult.branchNotSelected(node);
    }

    private Instance getOrCreateNodeInstance(
            Long parentInstanceId,
            JobExecutionNode node,
            Map<Long, Instance> nodeInstanceMap,
            List<JobExecutionNode> upstreamNodes) {
        String inputVariables = mergeInputs(parentInstanceId, upstreamNodes, nodeInstanceMap);
        Instance child =
                nodeInstanceMap.computeIfAbsent(
                        node.id(),
                        key -> {
                            Long childId =
                                    instanceService.createChild(
                                            parentInstanceId, node, inputVariables);
                            return instanceService.getInstance(childId);
                        });
        if (child.getStatus() == InstanceStatus.WAITING
                && !Objects.equals(child.getInputVariables(), inputVariables)) {
            instanceService.updateInputVariables(child.getId(), inputVariables);
            child.setInputVariables(inputVariables);
        }
        return child;
    }

    private String mergeInputs(
            Long parentInstanceId,
            List<JobExecutionNode> upstreamNodes,
            Map<Long, Instance> nodeInstanceMap) {
        Instance parent = instanceService.getInstance(parentInstanceId);
        List<String> outputs =
                upstreamNodes.stream()
                        .map(JobExecutionNode::id)
                        .map(nodeInstanceMap::get)
                        .filter(Objects::nonNull)
                        .map(Instance::getOutputVariables)
                        .toList();
        return variableSnapshotService.mergeUpstreamOutputs(parent.getInputVariables(), outputs);
    }

    private NodeExecuteResult resultFromLatest(
            JobExecutionNode node, Long childInstanceId, Map<Long, Instance> nodeInstanceMap) {
        Instance latest = instanceService.getInstance(childInstanceId);
        if (latest.getStatus() == InstanceStatus.WAITING_EVENT) {
            nodeInstanceMap.put(node.id(), latest);
            return NodeExecuteResult.of(node, NodeExecuteStatus.WAITING_EVENT);
        }
        if (!latest.getStatus().isFinished()) {
            instanceService.completeTask(
                    childInstanceId,
                    InstanceStatus.FAILED,
                    CoreMessage.NODE_INCOMPLETE.defaultMessage(),
                    RuntimeErrorMetadata.from(CoreMessage.NODE_INCOMPLETE, new Object[0]));
            latest = instanceService.getInstance(childInstanceId);
        }
        nodeInstanceMap.put(node.id(), latest);
        return resultFromStatus(node, latest.getStatus());
    }

    private NodeExecuteResult resultFromStatus(JobExecutionNode node, InstanceStatus status) {
        NodeExecuteStatus nodeStatus =
                switch (status) {
                    case SUCCESS -> NodeExecuteStatus.SUCCESS;
                    case TIMED_OUT -> NodeExecuteStatus.TIMED_OUT;
                    case STOPPED -> NodeExecuteStatus.STOPPED;
                    case SKIPPED -> NodeExecuteStatus.SKIPPED;
                    case WAITING_EVENT -> NodeExecuteStatus.WAITING_EVENT;
                    case FAILED, WAITING, RUNNING, PAUSED -> NodeExecuteStatus.FAILED;
                };
        return NodeExecuteResult.of(node, nodeStatus);
    }

    private void failChildIfActive(Instance child) {
        if (child == null) {
            return;
        }
        Instance latest = instanceService.getInstance(child.getId());
        if (!latest.getStatus().isFinished()) {
            instanceService.completeTask(
                    child.getId(),
                    InstanceStatus.FAILED,
                    CoreMessage.NODE_FAILED.defaultMessage(),
                    RuntimeErrorMetadata.from(CoreMessage.NODE_FAILED, new Object[0]));
        }
    }

    private void stopChildIfActive(Instance child) {
        if (child == null) {
            return;
        }
        Instance latest = instanceService.getInstance(child.getId());
        if (!latest.getStatus().isFinished()) {
            instanceService.stop(child.getId());
        }
    }
}
