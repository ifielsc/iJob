package com.ifielsc.ijob.core.trigger;

import com.ifielsc.ijob.common.diagnostic.SafeDiagnostic;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.target.TargetRunner;
import com.ifielsc.ijob.core.execute.target.TargetRunnerFactory;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.ExecutionOwnershipLostException;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TriggerExecuteService {
    private final TriggerRuntimeService triggerRuntimeService;
    private final InstanceService instanceService;
    private final TargetRunnerFactory targetRunnerFactory;
    private final ExecutionFenceCoordinator fenceCoordinator;

    @Autowired
    public TriggerExecuteService(
            TriggerRuntimeService triggerRuntimeService,
            InstanceService instanceService,
            TargetRunnerFactory targetRunnerFactory,
            ExecutionFenceCoordinator fenceCoordinator) {
        this.triggerRuntimeService = triggerRuntimeService;
        this.instanceService = instanceService;
        this.targetRunnerFactory = targetRunnerFactory;
        this.fenceCoordinator = fenceCoordinator;
    }

    protected TriggerExecuteService(
            TriggerRuntimeService triggerRuntimeService,
            InstanceService instanceService,
            TargetRunnerFactory targetRunnerFactory) {
        this(triggerRuntimeService, instanceService, targetRunnerFactory, null);
    }

    public TriggerExecutionDisposition execute(Long triggerId) {
        Trigger trigger = triggerRuntimeService.getTrigger(triggerId);
        if (trigger == null) {
            return TriggerExecutionDisposition.COMPLETED;
        }

        if (trigger.getStatus() == TriggerStatus.WAITING) {
            if (!triggerRuntimeService.claimRunning(triggerId)) {
                return TriggerExecutionDisposition.COMPLETED;
            }
        } else if (trigger.getStatus() != TriggerStatus.RUNNING) {
            return TriggerExecutionDisposition.COMPLETED;
        }

        Long rootInstanceId = null;
        try {
            Instance existingRoot = instanceService.getRootByTriggerId(triggerId);
            rootInstanceId =
                    existingRoot == null ? instanceService.create(trigger) : existingRoot.getId();
            TriggerWorkClaim claim =
                    fenceCoordinator == null ? null : fenceCoordinator.activeClaim(triggerId);
            if (existingRoot != null && claim != null) {
                instanceService.adoptRoot(rootInstanceId, claim);
                existingRoot = instanceService.getInstance(rootInstanceId);
            }
            if (existingRoot != null && existingRoot.getStatus() != InstanceStatus.WAITING) {
                completeTrigger(triggerId, rootInstanceId);
                return disposition(rootInstanceId);
            }
            TargetRunner targetRunner = targetRunnerFactory.getRunner(trigger.getTargetType());
            targetRunner.run(rootInstanceId);
            completeTrigger(triggerId, rootInstanceId);
            return disposition(rootInstanceId);
        } catch (ExecutionOwnershipLostException exception) {
            log.warn("execution ownership lost, triggerId={}", triggerId);
            throw exception;
        } catch (Exception e) {
            log.error("execute fail, triggerId={}", triggerId, SafeDiagnostic.sanitized(e));

            if (rootInstanceId != null) {
                instanceService.completeTask(
                        rootInstanceId,
                        InstanceStatus.FAILED,
                        CoreMessage.EXECUTION_FAILED.defaultMessage(),
                        RuntimeErrorMetadata.from(CoreMessage.EXECUTION_FAILED, new Object[0]));
                completeTrigger(triggerId, rootInstanceId);
            } else {
                triggerRuntimeService.completeReason(
                        triggerId, TriggerStatus.FAILED, CoreMessage.INSTANCE_CREATION_FAILED);
            }
            return TriggerExecutionDisposition.COMPLETED;
        }
    }

    public void completeTrigger(Long triggerId, Long rootInstanceId) {
        Instance root = instanceService.getInstance(rootInstanceId);
        if (root == null) {
            triggerRuntimeService.completeReason(
                    triggerId, TriggerStatus.FAILED, CoreMessage.INSTANCE_MISSING);
            return;
        }
        String message = root.getMessage();
        if (root.getStatus().isFinished() && root.getMessageKey() != null) {
            triggerRuntimeService.completeFromRoot(triggerId, root);
            return;
        }
        switch (root.getStatus()) {
            case SUCCESS -> triggerRuntimeService.markSuccess(triggerId);
            case FAILED -> triggerRuntimeService.markFailed(triggerId, message);
            case STOPPED -> triggerRuntimeService.markStopped(triggerId, message);
            case TIMED_OUT -> triggerRuntimeService.markTimedOut(triggerId, message);
            case SKIPPED -> triggerRuntimeService.markSkipped(triggerId, message);
            case WAITING_EVENT -> {
                // Durable waits keep the trigger RUNNING until continuation.
            }
            case WAITING, RUNNING, PAUSED ->
                    triggerRuntimeService.completeReason(
                            triggerId, TriggerStatus.FAILED, CoreMessage.EXECUTION_INCOMPLETE);
        }
    }

    private TriggerExecutionDisposition disposition(Long rootInstanceId) {
        Instance root = instanceService.getInstance(rootInstanceId);
        return root != null && root.getStatus() == InstanceStatus.WAITING_EVENT
                ? TriggerExecutionDisposition.SUSPENDED
                : TriggerExecutionDisposition.COMPLETED;
    }
}
