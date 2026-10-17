package com.ifelio.ijob.core.event;

import com.ifelio.ijob.common.diagnostic.SafeDiagnostic;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.error.CoreMessage;
import com.ifelio.ijob.core.execute.JobExecuteService;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifelio.ijob.core.trigger.TriggerCommand;
import com.ifelio.ijob.core.trigger.TriggerCommandHandler;
import com.ifelio.ijob.core.trigger.TriggerRuntimeService;
import com.ifelio.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.SuspendedWorkPermit;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkStatus;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@Slf4j
public class InstanceContinuationDispatcher {
    private final Executor executor;
    private final InstanceService instanceService;
    private final ObjectProvider<JobExecuteService> jobExecuteServiceProvider;
    private final TriggerRuntimeService triggerRuntimeService;
    private final TriggerCommandHandler triggerCommandHandler;
    private final DatabaseTriggerWorkQueue workQueue;
    private final ExecutionFenceCoordinator fenceCoordinator;

    @Autowired
    public InstanceContinuationDispatcher(
            @Qualifier("triggerExecutor") Executor executor,
            InstanceService instanceService,
            ObjectProvider<JobExecuteService> jobExecuteServiceProvider,
            TriggerRuntimeService triggerRuntimeService,
            TriggerCommandHandler triggerCommandHandler,
            DatabaseTriggerWorkQueue workQueue,
            ExecutionFenceCoordinator fenceCoordinator) {
        this.executor = executor;
        this.instanceService = instanceService;
        this.jobExecuteServiceProvider = jobExecuteServiceProvider;
        this.triggerRuntimeService = triggerRuntimeService;
        this.triggerCommandHandler = triggerCommandHandler;
        this.workQueue = workQueue;
        this.fenceCoordinator = fenceCoordinator;
    }

    public InstanceContinuationDispatcher(
            @Qualifier("triggerExecutor") Executor executor,
            InstanceService instanceService,
            ObjectProvider<JobExecuteService> jobExecuteServiceProvider,
            TriggerRuntimeService triggerRuntimeService) {
        this.executor = executor;
        this.instanceService = instanceService;
        this.jobExecuteServiceProvider = jobExecuteServiceProvider;
        this.triggerRuntimeService = triggerRuntimeService;
        this.triggerCommandHandler = null;
        this.workQueue = null;
        this.fenceCoordinator = null;
    }

    @Transactional
    public void dispatchAfterCommit(Long rootInstanceId) {
        if (fenceCoordinator != null && fenceCoordinator.isEnforced()) {
            Long triggerId = preparePersistentContinuation(rootInstanceId);
            if (triggerId == null) {
                return;
            }
            afterCommit(() -> triggerCommandHandler.handle(new TriggerCommand(triggerId)));
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatchLocal(rootInstanceId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchLocal(rootInstanceId);
                    }
                });
    }

    private Long preparePersistentContinuation(Long rootInstanceId) {
        Instance root = instanceService.getInstance(rootInstanceId);
        if (root == null || root.getTargetType() != TargetType.JOB || root.getTriggerId() == null) {
            return null;
        }
        Long triggerId = root.getTriggerId();
        var work = workQueue.get(triggerId);
        if (work == null || work.getStatus() != TriggerWorkStatus.SUSPENDED) {
            return null;
        }
        SuspendedWorkPermit permit = new SuspendedWorkPermit(triggerId, work.getFenceToken());
        return fenceCoordinator.withSuspendedAccess(
                permit,
                () -> {
                    if (!instanceService.claimContinuation(rootInstanceId)) {
                        return null;
                    }
                    if (!workQueue.resumeSuspended(permit)) {
                        throw new IllegalStateException(
                                "Suspended trigger work could not be resumed");
                    }
                    return triggerId;
                });
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // Release the submitting transaction before claiming resumed work.
                        try {
                            executor.execute(action);
                        } catch (RejectedExecutionException exception) {
                            log.error(
                                    "persistent continuation dispatch deferred to scanner",
                                    SafeDiagnostic.sanitized(exception));
                        }
                    }
                });
    }

    private void dispatchLocal(Long rootInstanceId) {
        try {
            executor.execute(() -> continueJob(rootInstanceId));
        } catch (RejectedExecutionException exception) {
            log.error(
                    "event continuation rejected, rootInstanceId={}",
                    rootInstanceId,
                    SafeDiagnostic.sanitized(exception));
        }
    }

    private void continueJob(Long rootInstanceId) {
        try {
            Instance root = instanceService.getInstance(rootInstanceId);
            if (root == null
                    || root.getTargetType() != TargetType.JOB
                    || !instanceService.claimContinuation(rootInstanceId)) {
                return;
            }
            jobExecuteServiceProvider.getObject().execute(rootInstanceId);
            completeTrigger(instanceService.getInstance(rootInstanceId));
        } catch (Exception exception) {
            log.error(
                    "event continuation failed, rootInstanceId={}",
                    rootInstanceId,
                    SafeDiagnostic.sanitized(exception));
            failActiveRoot(rootInstanceId);
            completeTrigger(instanceService.getInstance(rootInstanceId));
        }
    }

    private void failActiveRoot(Long rootInstanceId) {
        Instance root = instanceService.getInstance(rootInstanceId);
        if (root == null || root.getStatus().isFinished()) {
            return;
        }
        try {
            instanceService.completeTask(
                    rootInstanceId,
                    InstanceStatus.FAILED,
                    CoreMessage.EVENT_CONTINUATION_FAILED.defaultMessage(),
                    RuntimeErrorMetadata.from(
                            CoreMessage.EVENT_CONTINUATION_FAILED, new Object[0]));
        } catch (RuntimeException failure) {
            log.error(
                    "event continuation failure could not be persisted, " + "rootInstanceId={}",
                    rootInstanceId,
                    SafeDiagnostic.sanitized(failure));
        }
    }

    private void completeTrigger(Instance root) {
        if (root == null || root.getTriggerId() == null || !root.getStatus().isFinished()) {
            return;
        }
        triggerRuntimeService.completeFromRoot(root.getTriggerId(), root);
    }
}
