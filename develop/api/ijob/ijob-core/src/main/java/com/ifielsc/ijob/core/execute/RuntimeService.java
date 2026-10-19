package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.core.event.EventWaitResolutionService;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.infra.entity.runtime.Instance;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuntimeService {
    private final ExecutionManager executionManager;
    private final ExecutionTreeControl executionTreeControl;
    private final InstanceService instanceService;
    private final TaskRetryService taskRetryService;
    private final JobRetryService jobRetryService;
    private final EventWaitResolutionService eventWaitResolutionService;
    private final InstanceRuntimeService instanceRuntimeService;

    @Autowired
    public RuntimeService(
            ExecutionManager executionManager,
            ExecutionTreeControl executionTreeControl,
            InstanceService instanceService,
            TaskRetryService taskRetryService,
            JobRetryService jobRetryService,
            ObjectProvider<EventWaitResolutionService> waitProvider,
            InstanceRuntimeService instanceRuntimeService) {
        this(
                executionManager,
                executionTreeControl,
                instanceService,
                taskRetryService,
                jobRetryService,
                waitProvider.getIfAvailable(),
                instanceRuntimeService);
    }

    public RuntimeService(
            ExecutionManager executionManager,
            ExecutionTreeControl executionTreeControl,
            InstanceService instanceService,
            TaskRetryService taskRetryService,
            JobRetryService jobRetryService) {
        this(
                executionManager,
                executionTreeControl,
                instanceService,
                taskRetryService,
                jobRetryService,
                (EventWaitResolutionService) null,
                null);
    }

    RuntimeService(
            ExecutionManager executionManager,
            ExecutionTreeControl executionTreeControl,
            InstanceService instanceService,
            TaskRetryService taskRetryService,
            JobRetryService jobRetryService,
            EventWaitResolutionService eventWaitResolutionService,
            InstanceRuntimeService instanceRuntimeService) {
        this.executionManager = executionManager;
        this.executionTreeControl = executionTreeControl;
        this.instanceService = instanceService;
        this.taskRetryService = taskRetryService;
        this.jobRetryService = jobRetryService;
        this.eventWaitResolutionService = eventWaitResolutionService;
        this.instanceRuntimeService = instanceRuntimeService;
    }

    public void stop(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }

        if (instance.getStatus().isFinished()) {
            return;
        }

        executionTreeControl.requestStop(instanceId);
        if (eventWaitResolutionService != null) {
            if (instance.getId().equals(instance.getRootInstanceId())) {
                eventWaitResolutionService.stopRoot(instance.getId());
            } else {
                eventWaitResolutionService.stopInstance(instance.getId());
            }
        }
        if (instanceRuntimeService != null) {
            // Persist the winning terminal state and enqueue its notification together.
            // A worker losing the completion race will not enqueue a second notification.
            instanceRuntimeService.stop(instanceId);
        } else {
            instanceService.stop(instanceId);
        }
    }

    public void pause(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }

        if (instance.getStatus() != InstanceStatus.RUNNING) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Instance not running");
        }

        if (!executionTreeControl.requestPause(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Instance is not active in this process");
        }
        try {
            instanceService.pause(instanceId);
        } catch (RuntimeException exception) {
            executionTreeControl.requestResume(instanceId);
            throw exception;
        }
    }

    public void resume(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }

        if (instance.getStatus() != InstanceStatus.PAUSED) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Instance not paused");
        }

        if (!executionTreeControl.requestResume(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Instance is not active in this process");
        }
        try {
            instanceService.resume(instanceId);
        } catch (RuntimeException exception) {
            executionTreeControl.requestPause(instanceId);
            throw exception;
        }
    }

    public void retry(Long instanceId) {
        retry(instanceId, false);
    }

    public void retryOwned(Long instanceId) {
        retry(instanceId, true);
    }

    private void retry(Long instanceId, boolean owned) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }
        if (!instance.getStatus().isRetryable()) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Only failed or timed out instances can be retried");
        }
        requireRootInstance(instance);
        if (executionTreeControl.hasActiveExecutionInTree(instanceId)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Instance tree is still active in this process");
        }
        switch (instance.getTargetType()) {
            case TASK -> taskRetryService.retry(instanceId);
            case JOB -> {
                if (owned) {
                    jobRetryService.retryFailedNodesOwned(instanceId);
                } else {
                    jobRetryService.retryFailedNodes(instanceId);
                }
            }
        }
    }

    private void requireRootInstance(Instance instance) {
        if (instance.getParentInstanceId() != null
                || instance.getRootInstanceId() == null
                || !instance.getId().equals(instance.getRootInstanceId())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Only root instances can be retried");
        }
    }
}
