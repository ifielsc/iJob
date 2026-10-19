// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.graph.DagCoordinationResult;
import com.ifielsc.ijob.core.execute.graph.DagCoordinator;
import com.ifielsc.ijob.core.execute.graph.DagExecutionResult;
import com.ifielsc.ijob.core.execute.graph.DagResultAggregator;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.execute.graph.JobExecutionPlanBuilder;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;
import com.ifielsc.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class JobExecuteService {
    private final VariableResolver variableResolver;
    private final JobRuntimeNestingGuard jobRuntimeNestingGuard;
    private final VariableSnapshotService variableSnapshotService;

    private final RuntimeDefinitionService runtimeDefinitionService;
    private final JobExecutionPlanBuilder jobExecutionPlanBuilder;
    private final DagCoordinator dagCoordinator;
    private final DagResultAggregator dagResultAggregator;
    private final InstanceService instanceService;
    private final InstanceLogService instanceLogService;
    private final InstanceRuntimeService instanceRuntimeService;
    private final ExecutionManager executionManager;
    private final ExecutionTreeControl executionTreeControl;
    private final ExecutorService jobExecutor;

    public JobExecuteService(
            VariableResolver variableResolver,
            JobRuntimeNestingGuard jobRuntimeNestingGuard,
            VariableSnapshotService variableSnapshotService,
            RuntimeDefinitionService runtimeDefinitionService,
            JobExecutionPlanBuilder jobExecutionPlanBuilder,
            DagCoordinator dagCoordinator,
            DagResultAggregator dagResultAggregator,
            InstanceService instanceService,
            InstanceLogService instanceLogService,
            InstanceRuntimeService instanceRuntimeService,
            ExecutionManager executionManager,
            ExecutionTreeControl executionTreeControl,
            @Qualifier("jobExecutor") ExecutorService jobExecutor) {
        this.variableResolver = variableResolver;
        this.jobRuntimeNestingGuard = jobRuntimeNestingGuard;
        this.variableSnapshotService = variableSnapshotService;
        this.runtimeDefinitionService = runtimeDefinitionService;
        this.jobExecutionPlanBuilder = jobExecutionPlanBuilder;
        this.dagCoordinator = dagCoordinator;
        this.dagResultAggregator = dagResultAggregator;
        this.instanceService = instanceService;
        this.instanceLogService = instanceLogService;
        this.instanceRuntimeService = instanceRuntimeService;
        this.executionManager = executionManager;
        this.executionTreeControl = executionTreeControl;
        this.jobExecutor = jobExecutor;
    }

    public void execute(Long instanceId) {
        await(instanceId, executeAsync(instanceId));
    }

    public CompletableFuture<Void> executeAsync(Long instanceId) {
        Instance instance = requireInstance(instanceId);
        jobRuntimeNestingGuard.validateInstance(instance);
        JobExecutionSpec spec =
                runtimeDefinitionService.loadJob(
                        instance.getTargetId(), instance.getDefinitionId());
        JobExecutionPlan plan = jobExecutionPlanBuilder.build(spec.jobDefinitionId());
        return startExecution(instanceId, instance.getTriggerId(), spec, plan, null);
    }

    public void executeRetry(
            Long instanceId, JobExecutionSpec spec, JobExecutionPlan plan, Set<Long> retryNodeIds) {
        Instance instance = requireInstance(instanceId);
        jobRuntimeNestingGuard.validateInstance(instance);
        await(
                instanceId,
                startExecution(instanceId, instance.getTriggerId(), spec, plan, retryNodeIds));
    }

    private Instance requireInstance(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Job instance not found");
        }
        return instance;
    }

    private CompletableFuture<Void> startExecution(
            Long instanceId,
            Long triggerId,
            JobExecutionSpec spec,
            JobExecutionPlan plan,
            Set<Long> retryNodeIds) {
        long started = System.nanoTime();
        ExecutionHandle handle = executionManager.register(instanceId, triggerId);
        TaskContext context =
                new TaskContext(
                        instanceId, handle, instanceService, instanceLogService, variableResolver);
        var result = new CompletableFuture<Void>();
        var coordination = new AtomicReference<CompletableFuture<DagCoordinationResult>>();
        var initial = new AtomicReference<Future<?>>();
        result.whenComplete(
                (value, failure) -> {
                    try {
                        if (result.isCancelled()) {
                            try {
                                executionTreeControl.requestStop(instanceId);
                            } finally {
                                Future<?> worker = initial.get();
                                if (worker != null) worker.cancel(true);
                                var active = coordination.get();
                                if (active != null) active.cancel(true);
                            }
                        }
                    } finally {
                        executionManager.unregister(instanceId);
                    }
                });
        try {
            initial.set(
                    jobExecutor.submit(
                            () -> {
                                try {
                                    context.checkStop();
                                    instanceRuntimeService.running(instanceId);
                                    context.logInfo(
                                            "Job orchestration started; nodes="
                                                    + plan.nodes().size()
                                                    + "; retry="
                                                    + (retryNodeIds != null));
                                    List<Instance> children =
                                            instanceService.listChildren(instanceId);
                                    Map<Long, Instance> nodeInstances = mapNodeInstances(children);
                                    AtomicInteger lastProgress = new AtomicInteger(-1);
                                    var active =
                                            dagCoordinator.coordinateAsync(
                                                    instanceId,
                                                    plan,
                                                    loadHistoryResult(children, retryNodeIds),
                                                    loadInactiveHistoryNodeIds(
                                                            children, retryNodeIds),
                                                    retryNodeIds == null ? Set.of() : retryNodeIds,
                                                    nodeInstances,
                                                    context,
                                                    count ->
                                                            refreshProgress(
                                                                    instanceId,
                                                                    count,
                                                                    plan.nodes().size(),
                                                                    lastProgress),
                                                    jobExecutor,
                                                    started,
                                                    spec.timeout());
                                    coordination.set(active);
                                    if (result.isCancelled()) active.cancel(true);
                                    active.whenComplete(
                                            (outcome, failure) -> {
                                                try {
                                                    if (failure != null) throw unwrap(failure);
                                                    context.checkStop();
                                                    if (outcome.suspended()) {
                                                        context.logInfo(
                                                                "Job orchestration suspended;"
                                                                        + " waiting for event");
                                                        instanceRuntimeService.waitingEvent(
                                                                instanceId);
                                                    } else {
                                                        DagExecutionResult completed =
                                                                dagResultAggregator.aggregate(
                                                                        outcome,
                                                                        plan.nodes().size());
                                                        instanceService.updateOutputVariables(
                                                                instanceId,
                                                                aggregateJobOutputs(
                                                                        plan, nodeInstances));
                                                        completeDag(instanceId, completed);
                                                        context.logInfo(
                                                                "Job orchestration finished;"
                                                                        + " result="
                                                                        + completed.status());
                                                    }
                                                    result.complete(null);
                                                } catch (Throwable error) {
                                                    finishFailure(
                                                            instanceId,
                                                            handle,
                                                            context,
                                                            result,
                                                            error);
                                                }
                                            });
                                } catch (Throwable failure) {
                                    finishFailure(instanceId, handle, context, result, failure);
                                }
                            }));
            if (result.isCancelled()) initial.get().cancel(true);
        } catch (RuntimeException rejected) {
            finishFailure(instanceId, handle, context, result, rejected);
        }
        return result;
    }

    private void finishFailure(
            Long instanceId,
            ExecutionHandle handle,
            TaskContext context,
            CompletableFuture<Void> result,
            Throwable failure) {
        try {
            if (handle.isTimeoutRequested()) {
                instanceRuntimeService.completeTask(
                        instanceId, TaskResult.timeout(CoreMessage.JOB_TIMEOUT));
                result.completeExceptionally(new ExecutionTimeoutException("Job timeout", failure));
            } else if (handle.isStopRequested()
                    || failure instanceof StopException
                    || failure instanceof CancellationException) {
                instanceRuntimeService.stop(instanceId);
                result.completeExceptionally(new StopException());
            } else {
                context.logError(
                        "Job orchestration failed; inspect execution status and system trace");
                log.error(
                        "job instance execution failed, instanceId={}",
                        instanceId,
                        SafeDiagnostic.sanitized(failure));
                instanceRuntimeService.fail(instanceId, null);
                result.completeExceptionally(
                        new ExecutionFailedException("Job execution failed", failure));
            }
        } catch (Throwable finalizationFailure) {
            if (finalizationFailure != failure) finalizationFailure.addSuppressed(failure);
            result.completeExceptionally(finalizationFailure);
        }
    }

    private RuntimeException unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure instanceof RuntimeException runtime
                ? runtime
                : new ExecutionFailedException("Job execution failed", failure);
    }

    private Set<Long> loadInactiveHistoryNodeIds(List<Instance> children, Set<Long> retryNodeIds) {
        Set<Long> result = new HashSet<>();
        for (Instance child : children) {
            Long nodeId = child.getDefinitionNodeId();
            if ((retryNodeIds == null || !retryNodeIds.contains(nodeId))
                    && child.getStatus() == InstanceStatus.SKIPPED
                    && "IJOB-DAG-0001".equals(child.getErrorCode())) {
                result.add(nodeId);
            }
        }
        return Set.copyOf(result);
    }

    String aggregateJobOutputs(JobExecutionPlan plan, Map<Long, Instance> nodeInstanceMap) {
        List<String> terminalOutputs =
                plan.nodes().stream()
                        .filter(node -> plan.childMap().get(node.id()).isEmpty())
                        .map(node -> nodeInstanceMap.get(node.id()))
                        .filter(Objects::nonNull)
                        .filter(instance -> instance.getStatus() == InstanceStatus.SUCCESS)
                        .map(Instance::getOutputVariables)
                        .toList();
        return variableSnapshotService.mergeUpstreamOutputs(null, terminalOutputs);
    }

    private void await(Long instanceId, CompletableFuture<Void> result) {
        try {
            result.get();
        } catch (InterruptedException interrupted) {
            executionTreeControl.requestStop(instanceId);
            result.cancel(true);
            Thread.currentThread().interrupt();
            instanceRuntimeService.stop(instanceId);
            throw new StopException();
        } catch (ExecutionException failure) {
            throw unwrap(failure);
        } catch (CancellationException cancelled) {
            throw new StopException();
        }
    }

    private Map<Long, InstanceStatus> loadHistoryResult(
            List<Instance> children, Set<Long> retryNodeIds) {
        Map<Long, InstanceStatus> result = new ConcurrentHashMap<>();
        for (Instance child : children) {
            Long nodeId = child.getDefinitionNodeId();
            if (nodeId == null || nodeId <= 0) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Job child instance snapshot is missing");
            }
            if (retryNodeIds != null && retryNodeIds.contains(nodeId)) {
                continue;
            }
            if (child.getStatus() == InstanceStatus.WAITING
                    || child.getStatus() == InstanceStatus.WAITING_EVENT) {
                continue;
            }
            result.put(nodeId, child.getStatus());
        }
        return result;
    }

    private Map<Long, Instance> mapNodeInstances(List<Instance> children) {
        Map<Long, Instance> result = new ConcurrentHashMap<>();
        for (Instance child : children) {
            Long nodeId = child.getDefinitionNodeId();
            if (nodeId == null || nodeId <= 0 || result.putIfAbsent(nodeId, child) != null) {
                throw BusinessException.localized(
                        ResultCode.CONFLICT, "Job child instance snapshot is invalid");
            }
        }
        return result;
    }

    private void completeDag(Long instanceId, DagExecutionResult result) {
        switch (result.status()) {
            case SUCCESS -> instanceRuntimeService.success(instanceId);
            case FAILED ->
                    instanceRuntimeService.completeTask(
                            instanceId, TaskResult.fail(CoreMessage.DAG_FAILED));
            case STOPPED -> instanceRuntimeService.stop(instanceId);
            default ->
                    throw BusinessException.localized(
                            ResultCode.SYSTEM_ERROR, "Job DAG result is invalid");
        }
    }

    private void refreshProgress(
            Long instanceId, int finishedCount, int totalNodeCount, AtomicInteger lastProgress) {
        int progress = totalNodeCount == 0 ? 0 : (int) (finishedCount * 100L / totalNodeCount);
        while (true) {
            int oldProgress = lastProgress.get();
            if (progress <= oldProgress) {
                return;
            }
            if (lastProgress.compareAndSet(oldProgress, progress)) {
                instanceService.updateProgress(instanceId, progress);
                return;
            }
        }
    }
}
