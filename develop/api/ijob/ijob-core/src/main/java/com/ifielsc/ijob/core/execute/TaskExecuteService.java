package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.diagnostic.DiagnosticContext;
import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.core.concurrency.LockHandle;
import com.ifielsc.ijob.core.concurrency.LockService;
import com.ifielsc.ijob.core.concurrency.PermitWaitTimeoutException;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.execute.task.TaskRunner;
import com.ifielsc.ijob.core.execute.task.TaskRunnerFactory;
import com.ifielsc.ijob.core.execute.task.TaskRunnerUnavailableException;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;
import com.ifielsc.ijob.core.instance.InstanceLogService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifielsc.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;
import com.ifielsc.ijob.core.trigger.TriggerRuntimeService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class TaskExecuteService {
    private final VariableResolver variableResolver;
    private final VariableSnapshotService variableSnapshotService;

    private final RuntimeDefinitionService runtimeDefinitionService;
    private final InstanceService instanceService;
    private final InstanceLogService instanceLogService;
    private final InstanceRuntimeService instanceRuntimeService;
    private final LockService lockService;
    private final TaskRunnerFactory taskRunnerFactory;
    private final ExecutionManager executionManager;
    private final TriggerRuntimeService triggerRuntimeService;
    private final ExecutorService taskExecutor;

    public TaskExecuteService(
            VariableResolver variableResolver,
            VariableSnapshotService variableSnapshotService,
            RuntimeDefinitionService runtimeDefinitionService,
            InstanceService instanceService,
            InstanceLogService instanceLogService,
            InstanceRuntimeService instanceRuntimeService,
            LockService lockService,
            TaskRunnerFactory taskRunnerFactory,
            ExecutionManager executionManager,
            TriggerRuntimeService triggerRuntimeService,
            @Qualifier("taskExecutor") ExecutorService taskExecutor) {
        this.variableResolver = variableResolver;
        this.variableSnapshotService = variableSnapshotService;
        this.runtimeDefinitionService = runtimeDefinitionService;
        this.instanceService = instanceService;
        this.instanceLogService = instanceLogService;
        this.instanceRuntimeService = instanceRuntimeService;
        this.lockService = lockService;
        this.taskRunnerFactory = taskRunnerFactory;
        this.executionManager = executionManager;
        this.triggerRuntimeService = triggerRuntimeService;
        this.taskExecutor = taskExecutor;
    }

    public void execute(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Task instance not found");
        }
        if (instance.getTargetType() != TargetType.TASK) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Instance is not a task");
        }
        TaskAdmissionSnapshot snapshot =
                runtimeDefinitionService.loadTask(
                        instance.getTargetId(), instance.getTaskDefinitionId());
        TaskExecutionSpec spec = snapshot.executionSpec();
        LockHandle handle;
        try {
            java.time.LocalDateTime permitWaitDeadline = lockService.permitWaitDeadline();
            handle =
                    lockService.acquireTask(
                            instanceId,
                            snapshot.taskId(),
                            spec.getPluginType(),
                            () ->
                                    instanceRuntimeService.waitingPermit(
                                            instanceId, permitWaitDeadline));
        } catch (PermitWaitTimeoutException exception) {
            instanceRuntimeService.permitWaitTimeout(instanceId, exception.getMessage());
            return;
        } catch (StopException exception) {
            instanceRuntimeService.stop(instanceId);
            return;
        }
        java.util.Map<String, String> correlation = org.slf4j.MDC.getCopyOfContextMap();
        if (correlation == null) correlation = new java.util.HashMap<>();
        correlation.put("instanceId", String.valueOf(instanceId));
        correlation.put("rootInstanceId", String.valueOf(instance.getRootInstanceId()));
        correlation.put("triggerId", String.valueOf(instance.getTriggerId()));
        correlation.putIfAbsent("traceId", "trigger-" + instance.getTriggerId());
        try (var ignored = new DiagnosticContext(correlation)) {
            doExecute(instanceId, instance.getTriggerId(), spec);
        } finally {
            lockService.release(handle);
        }
    }

    private void doExecute(Long instanceId, Long triggerId, TaskExecutionSpec spec) {
        ExecutionHandle handle = executionManager.register(instanceId, triggerId);
        TaskContext context = null;

        try {
            instanceRuntimeService.running(instanceId);
            TaskRunner runner = taskRunnerFactory.getRunner(spec.getPluginType());
            Instance runtimeInstance = instanceService.getInstance(instanceId);
            Trigger trigger = loadTrigger(runtimeInstance);
            Map<String, Object> inputVariables =
                    variableSnapshotService.buildTaskInputs(runtimeInstance, trigger);
            context =
                    new TaskContext(
                            instanceId,
                            handle,
                            instanceService,
                            instanceLogService,
                            variableResolver,
                            inputVariables);
            seedInboundEvent(runtimeInstance, trigger, context);
            handle.bind(runner, spec, context);

            TaskResult result = executeWithRetry(runner, spec, context);
            handle.checkStop();
            if (result != null && result.getStatus() == InstanceStatus.WAITING_EVENT) {
                complete(instanceId, result);
                return;
            }
            instanceService.updateOutputVariables(
                    instanceId,
                    variableSnapshotService.serializeOutputs(context.getOutputVariables()));
            complete(instanceId, result);
        } catch (TaskRunnerUnavailableException e) {
            instanceRuntimeService.completeTask(
                    instanceId, TaskResult.fail(CoreMessage.RUNNER_UNAVAILABLE));
        } catch (ExecutionTimeoutException e) {
            instanceRuntimeService.completeTask(
                    instanceId, TaskResult.timeout(CoreMessage.TASK_TIMEOUT));
        } catch (StopException e) {
            if (handle.isTimeoutRequested())
                instanceRuntimeService.completeTask(
                        instanceId, TaskResult.timeout(CoreMessage.TASK_TIMEOUT));
            else instanceRuntimeService.stop(instanceId);
        } catch (Exception e) {
            if (handle.isTimeoutRequested()) {
                instanceRuntimeService.completeTask(
                        instanceId, TaskResult.timeout(CoreMessage.TASK_TIMEOUT));
            } else if (handle.isStopRequested()) {
                instanceRuntimeService.stop(instanceId);
            } else {
                log.error(
                        "Task execution failed, instanceId={}, diagnostic={}",
                        instanceId,
                        SafeDiagnostic.describe(e));
                if (e instanceof BusinessException business && !business.isCustomMessage()) {
                    instanceRuntimeService.completeTask(
                            instanceId,
                            TaskResult.builder()
                                    .status(InstanceStatus.FAILED)
                                    .message(business.getMessage())
                                    .messageMetadata(RuntimeErrorMetadata.from(business))
                                    .build());
                } else {
                    instanceRuntimeService.completeTask(
                            instanceId, TaskResult.fail(CoreMessage.TASK_FAILED));
                }
            }
        } finally {
            if (context != null) {
                context.cleanupResources();
            }
            handle.unbind();
            executionManager.unregister(instanceId);
        }
    }

    private Trigger loadTrigger(Instance instance) {
        if (instance == null || instance.getTriggerId() == null) {
            return null;
        }
        return triggerRuntimeService.getTrigger(instance.getTriggerId());
    }

    private void seedInboundEvent(Instance instance, Trigger trigger, TaskContext context) {
        if (instance == null
                || instance.getTriggerType() != TriggerType.EVENT
                || instance.getTriggerId() == null) {
            return;
        }
        if (trigger != null && trigger.getEventCode() != null) {
            context.seedInboundEvent(trigger.getEventId(), trigger.getEventCode());
        }
    }

    private TaskResult executeWithRetry(
            TaskRunner runner, TaskExecutionSpec spec, TaskContext context) {
        com.ifielsc.ijob.core.task.definition.TaskRetryPolicy.validate(
                spec.getRetryTimes(), spec.getRetryInterval());
        int maxAttempt = Math.addExact(spec.getRetryTimes(), 1);
        int retryInterval = spec.getRetryInterval();

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempt; attempt++) {
            var correlation = org.slf4j.MDC.getCopyOfContextMap();
            if (correlation == null) correlation = new java.util.HashMap<String, String>();
            correlation.put("taskAttempt", String.valueOf(attempt));
            try (var ignored = new DiagnosticContext(correlation)) {
                TaskResult result = executeWithTimeout(runner, spec, context);
                if (result == null || !result.isRetryableFailure() || attempt >= maxAttempt) {
                    return result;
                }
                log.warn(
                        "Task retry scheduled, instanceId={}, taskAttempt={},"
                                + " outcome=failed-result",
                        context.getInstanceId(),
                        attempt);
                context.cleanupResources();
                context.resetAttemptVariables();
                sleep(retryInterval);
            } catch (ExecutionTimeoutException | StopException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn(
                        "Task attempt failed, instanceId={}, taskAttempt={}, diagnostic={}",
                        context.getInstanceId(),
                        attempt,
                        SafeDiagnostic.describe(e));
                if (attempt >= maxAttempt) {
                    break;
                }

                context.cleanupResources();
                context.resetAttemptVariables();
                sleep(retryInterval);
            }
        }

        if (lastException instanceof BusinessException business) throw business;
        throw new ExecutionFailedException("Task execution failed", lastException);
    }

    private TaskResult executeWithTimeout(
            TaskRunner runner, TaskExecutionSpec spec, TaskContext context) throws Exception {
        context.checkStop();
        context.checkPause();
        context.checkInterrupted();

        java.util.Map<String, String> correlation = org.slf4j.MDC.getCopyOfContextMap();
        if (correlation == null) correlation = new java.util.HashMap<>();
        correlation.put("instanceId", String.valueOf(context.getInstanceId()));
        final java.util.Map<String, String> taskCorrelation = correlation;
        Future<TaskResult> future =
                taskExecutor.submit(
                        () -> {
                            try (var ignored = new DiagnosticContext(taskCorrelation)) {
                                return runner.run(spec, context);
                            }
                        });
        try {
            if (spec.getTimeout() != null && spec.getTimeout() > 0) {
                return future.get(spec.getTimeout(), TimeUnit.SECONDS);
            }
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Task execution failed", cause);
        } catch (CancellationException e) {
            throw new StopException();
        } catch (TimeoutException e) {
            context.cancel(com.ifielsc.ijob.core.execute.task.CancellationReason.TIMEOUT);
            future.cancel(true);
            try {
                runner.stop(spec, context);
            } catch (Exception stopException) {
                log.warn(
                        "Task timeout cleanup failed, instanceId={}",
                        context.getInstanceId(),
                        SafeDiagnostic.sanitized(stopException));
            }
            throw new ExecutionTimeoutException("Task timeout", e);
        } catch (InterruptedException e) {
            context.cancel(com.ifielsc.ijob.core.execute.task.CancellationReason.STOP);
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new StopException();
        }
    }

    private void complete(Long instanceId, TaskResult result) {
        if (result == null || result.getStatus() == null) {
            instanceRuntimeService.completeTask(
                    instanceId, TaskResult.fail(CoreMessage.TASK_RESULT_MISSING));
            return;
        }
        InstanceStatus status = result.getStatus();
        switch (status) {
            case SUCCESS, FAILED, STOPPED, TIMED_OUT, SKIPPED ->
                    instanceRuntimeService.completeTask(instanceId, result);
            case WAITING_EVENT -> instanceRuntimeService.waitingEvent(instanceId);
            case WAITING, RUNNING, PAUSED ->
                    instanceRuntimeService.completeTask(
                            instanceId, TaskResult.fail(CoreMessage.TASK_RESULT_INVALID));
        }
    }

    private void sleep(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return;
        }

        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StopException();
        }
    }
}
