package com.ifelio.ijob.core.instance;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.job.definition.JobAdmissionResolver;
import com.ifelio.ijob.core.job.definition.JobRuntimeNestingGuard;
import com.ifelio.ijob.core.runtime.RuntimeErrorMetadata;
import com.ifelio.ijob.core.state.InstanceStateMachine;
import com.ifelio.ijob.core.task.definition.TaskAdmissionResolver;
import com.ifelio.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifelio.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.coordination.work.ExecutionOwnershipLostException;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkClaim;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class InstanceService {
    private final InstanceMapper instanceMapper;
    private final JobRuntimeNestingGuard jobRuntimeNestingGuard;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final DatabaseTriggerWorkQueue workQueue;
    private final com.ifelio.ijob.infra.coordination.RuntimeMutationGuard mutationGuard;

    @Autowired
    public InstanceService(
            InstanceMapper instanceMapper,
            JobRuntimeNestingGuard jobRuntimeNestingGuard,
            ExecutionFenceCoordinator fenceCoordinator,
            DatabaseTriggerWorkQueue workQueue,
            AppProperties ignoredAppProperties,
            com.ifelio.ijob.infra.coordination.RuntimeMutationGuard mutationGuard) {
        this.instanceMapper = instanceMapper;
        this.jobRuntimeNestingGuard = jobRuntimeNestingGuard;
        this.fenceCoordinator = fenceCoordinator;
        this.workQueue = workQueue;
        this.mutationGuard = mutationGuard;
    }

    public InstanceService(
            InstanceMapper instanceMapper, JobRuntimeNestingGuard jobRuntimeNestingGuard) {
        this.instanceMapper = instanceMapper;
        this.jobRuntimeNestingGuard = jobRuntimeNestingGuard;
        this.fenceCoordinator = null;
        this.workQueue = null;
        this.mutationGuard = null;
    }

    public InstanceService(
            InstanceMapper instanceMapper,
            TaskAdmissionResolver ignoredTaskResolver,
            JobAdmissionResolver ignoredJobResolver,
            JobRuntimeNestingGuard jobRuntimeNestingGuard) {
        this(instanceMapper, jobRuntimeNestingGuard);
    }

    public List<Instance> listNodeInstances(Long jobInstanceId) {
        return instanceMapper.selectByParentInstanceId(jobInstanceId);
    }

    public List<Instance> listChildren(Long parentInstanceId) {
        if (parentInstanceId == null) {
            return Collections.emptyList();
        }
        return instanceMapper.selectByParentInstanceId(parentInstanceId);
    }

    public Instance getInstance(Long id) {
        return instanceMapper.selectById(id);
    }

    public Instance getRootByTriggerId(Long triggerId) {
        return instanceMapper.selectRootByTriggerId(triggerId);
    }

    @Transactional
    public Long create(Trigger trigger) {
        lockConcurrencyAdmission();
        Instance instance = new Instance();
        instance.setTargetType(trigger.getTargetType());
        instance.setTargetId(trigger.getTargetId());
        instance.setTriggerType(trigger.getTriggerType());
        instance.setTriggerId(trigger.getId());
        instance.setDefinitionId(trigger.getDefinitionId());
        instance.setTaskDefinitionId(trigger.getTaskDefinitionId());
        instance.setInputVariables(trigger.getVariables());
        instance.setStatus(InstanceStatus.WAITING);
        instance.setProgress(0);
        ExecutionFencePermit permit = permitForTrigger(trigger.getId());
        instance.setExecutionFence(permit.fenceToken() == null ? 0L : permit.fenceToken());
        requireInserted(
                instanceMapper.insert(instance, permit), instance.getId(), trigger.getId(), permit);

        instance.setRootInstanceId(instance.getId());
        if (instanceMapper.updateById(instance, permit) != 1) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Root instance initialization failed");
        }
        if (workQueue != null && permit.isClaimed()) {
            workQueue.bindRoot(requireClaim(trigger.getId()), instance.getId());
        }

        return instance.getId();
    }

    @Transactional
    public void running(Long instanceId) {
        if (instanceMapper.markRunningIfWaiting(
                        instanceId, LocalDateTime.now(), permitForInstance(instanceId))
                == 1) {
            return;
        }
        Instance latest = requireInstance(instanceId);
        if (latest.getStatus() == InstanceStatus.STOPPED
                || latest.getStatus() == InstanceStatus.TIMED_OUT) {
            throw new StopException();
        }
        throw conflict("Instance is not waiting");
    }

    @Transactional
    public void waitingPermit(Long instanceId, LocalDateTime deadline) {
        if (instanceMapper.markWaitingPermitIfWaiting(
                        instanceId,
                        "Waiting for concurrency permit",
                        deadline,
                        permitForInstance(instanceId))
                != 1) {
            Instance latest = requireInstance(instanceId);
            if (latest.getStatus() == InstanceStatus.STOPPED
                    || latest.getStatus() == InstanceStatus.TIMED_OUT) {
                throw new StopException();
            }
            throw conflict("Instance is not waiting for a concurrency permit");
        }
    }

    @Transactional
    public void stop(Long instanceId) {
        complete(instanceId, InstanceStatus.STOPPED, null);
    }

    @Transactional
    public void success(Long instanceId) {
        complete(instanceId, InstanceStatus.SUCCESS, null);
    }

    @Transactional
    public void fail(Long instanceId, String errorMessage) {
        complete(instanceId, InstanceStatus.FAILED, errorMessage);
    }

    @Transactional
    public void timeout(Long instanceId, String message) {
        complete(instanceId, InstanceStatus.TIMED_OUT, message);
    }

    @Transactional
    public void permitWaitTimeout(Long instanceId, String message) {
        complete(
                instanceId,
                InstanceStatus.TIMED_OUT,
                message,
                RuntimeErrorMetadata.permitWaitTimeout());
    }

    @Transactional
    public void skipped(Long instanceId) {
        complete(instanceId, InstanceStatus.SKIPPED, null);
    }

    @Transactional
    public Long createChild(Long parentInstanceId, JobExecutionNode node) {
        return createChild(parentInstanceId, node, null);
    }

    @Transactional
    public Long createChild(Long parentInstanceId, JobExecutionNode node, String inputVariables) {
        lockConcurrencyAdmission();
        Instance parent = instanceMapper.selectById(parentInstanceId);
        if (parent == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Parent instance not found");
        }
        validateParentAndNode(parent, node);
        if (node.nodeType() == TargetType.JOB) {
            jobRuntimeNestingGuard.validateChild(parent, node.refId());
        }

        Instance child = new Instance();
        child.setDefinitionNodeId(node.id());
        child.setParentInstanceId(parentInstanceId);
        child.setRootInstanceId(parent.getRootInstanceId());
        child.setTargetId(node.refId());
        child.setTargetType(node.nodeType());
        child.setTriggerType(parent.getTriggerType());
        child.setTriggerId(parent.getTriggerId());
        child.setInputVariables(
                inputVariables == null ? parent.getInputVariables() : inputVariables);
        child.setStatus(InstanceStatus.WAITING);
        child.setProgress(0);
        child.setExecutionFence(parent.getExecutionFence());
        applyChildSnapshot(parent, node, child);

        ExecutionFencePermit permit = permitForTrigger(parent.getTriggerId());
        requireInserted(
                instanceMapper.insert(child, permit), child.getId(), parent.getTriggerId(), permit);
        return child.getId();
    }

    @Transactional
    public void updateInputVariables(Long instanceId, String inputVariables) {
        if (instanceMapper.updateInputVariablesIfWaiting(
                        instanceId, inputVariables, permitForInstance(instanceId))
                != 1) {
            throw conflict("Instance input snapshot cannot be updated");
        }
    }

    @Transactional
    public void updateOutputVariables(Long instanceId, String outputVariables) {
        if (instanceMapper.updateOutputVariablesIfRunning(
                        instanceId, outputVariables, permitForInstance(instanceId))
                != 1) {
            // A terminal winner is authoritative; do not turn stop/timeout races into failures.
            Instance latest = instanceMapper.selectCurrentById(instanceId);
            if (latest != null && latest.getStatus().isFinished()) return;
            throw conflict("Instance output snapshot cannot be updated");
        }
    }

    @Transactional
    public void updateProgress(Long instanceId, Integer progress) {
        if (progress == null) {
            return;
        }

        progress = Math.max(0, Math.min(100, progress));
        if (instanceMapper.updateProgressIfRunning(
                        instanceId, progress, permitForInstance(instanceId))
                == 0) {
            requireInstance(instanceId);
        }
    }

    @Transactional
    public boolean claimRetry(Long instanceId) {
        lockRetryAdmission();
        Instance current = requireInstance(instanceId);
        if (!InstanceStateMachine.canRetry(current.getStatus())) {
            throw conflict("Instance cannot be retried");
        }
        if (instanceMapper.resetForRetry(
                        instanceId, current.getStatus(), permitForTrigger(current.getTriggerId()))
                == 1) {
            return true;
        }
        Instance latest = requireInstance(instanceId);
        if (latest.getStatus() == InstanceStatus.WAITING) {
            return false;
        }
        throw conflict("Instance cannot be reset for retry");
    }

    @Transactional
    public void resetJobBranch(Long instanceId) {
        lockRetryAdmission();
        Instance current = requireInstance(instanceId);
        if (!InstanceStateMachine.canResetJobBranch(current.getStatus())) {
            throw conflict("Job branch instance cannot be reset for retry");
        }
        if (instanceMapper.resetForRetry(
                        instanceId, current.getStatus(), permitForTrigger(current.getTriggerId()))
                == 1) {
            return;
        }
        Instance latest = requireInstance(instanceId);
        if (latest.getStatus() != InstanceStatus.WAITING) {
            throw conflict("Job branch instance cannot be reset for retry");
        }
    }

    @Transactional
    public void pause(Long instanceId) {
        requireTransition(InstanceStatus.RUNNING, InstanceStatus.PAUSED);
        if (!pauseIfRunning(instanceId)) {
            requireInstance(instanceId);
            throw conflict("Instance is not running");
        }
    }

    private void lockConcurrencyAdmission() {
        if (mutationGuard != null)
            mutationGuard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.CONCURRENCY);
    }

    private void lockRetryAdmission() {
        if (mutationGuard != null)
            mutationGuard.lock(com.ifelio.ijob.infra.coordination.RuntimeMutationGuard.LIFECYCLE);
        lockConcurrencyAdmission();
    }

    @Transactional
    public boolean pauseIfRunning(Long instanceId) {
        return instanceMapper.markPausedIfRunning(instanceId, permitForInstance(instanceId)) == 1;
    }

    @Transactional
    public void resume(Long instanceId) {
        requireTransition(InstanceStatus.PAUSED, InstanceStatus.RUNNING);
        if (!resumeIfPaused(instanceId)) {
            requireInstance(instanceId);
            throw conflict("Instance is not paused");
        }
    }

    @Transactional
    public boolean resumeIfPaused(Long instanceId) {
        return instanceMapper.markRunningIfPaused(instanceId, permitForInstance(instanceId)) == 1;
    }

    @Transactional
    public void waitingEvent(Long instanceId) {
        Instance current = requireInstance(instanceId);
        if (current.getStatus() == InstanceStatus.WAITING_EVENT) {
            return;
        }
        requireTransition(current.getStatus(), InstanceStatus.WAITING_EVENT);
        if (instanceMapper.markWaitingEventIfRunning(
                        instanceId, permitForTrigger(current.getTriggerId()))
                != 1) {
            throw conflict("Instance cannot enter event wait");
        }
    }

    @Transactional
    public boolean claimContinuation(Long instanceId) {
        Instance current = requireInstance(instanceId);
        if (current.getStatus() != InstanceStatus.WAITING_EVENT) {
            return false;
        }
        requireTransition(InstanceStatus.WAITING_EVENT, InstanceStatus.WAITING);
        return instanceMapper.resetWaitingEventForContinuation(
                        instanceId, permitForTrigger(current.getTriggerId()))
                == 1;
    }

    private void requireInserted(
            int affectedRows, Long generatedId, Long triggerId, ExecutionFencePermit permit) {
        if (affectedRows != 1 && permit.isClaimed()) {
            throw new ExecutionOwnershipLostException(triggerId);
        }
        if (affectedRows != 1 || generatedId == null || generatedId <= 0) {
            throw BusinessException.localized(ResultCode.SYSTEM_ERROR, "Instance creation failed");
        }
    }

    private Instance requireInstance(Long instanceId) {
        Instance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Instance not found");
        }
        return instance;
    }

    private void complete(Long instanceId, InstanceStatus nextStatus, String message) {
        complete(instanceId, nextStatus, message, null);
    }

    /** Persists terminal state and its message identity atomically. */
    @Transactional
    public boolean completeTask(
            Long instanceId, InstanceStatus status, String message, RuntimeErrorMetadata metadata) {
        if (status == null || !status.isFinished()) {
            throw new IllegalArgumentException("Task completion requires a terminal status");
        }
        return complete(instanceId, status, message, metadata);
    }

    private boolean complete(
            Long instanceId,
            InstanceStatus nextStatus,
            String message,
            RuntimeErrorMetadata suppliedMetadata) {
        Instance current = requireInstance(instanceId);
        if (current.getStatus() == nextStatus || current.getStatus().isFinished()) {
            return false;
        }
        requireTransition(current.getStatus(), nextStatus);

        Instance completion = new Instance();
        completion.setId(instanceId);
        completion.setStatus(nextStatus);
        completion.setEndTime(LocalDateTime.now());
        completion.setMessage(message);
        RuntimeErrorMetadata metadata =
                suppliedMetadata == null
                        ? RuntimeErrorMetadata.forStatus(nextStatus)
                        : suppliedMetadata;
        completion.setErrorCode(metadata.errorCode());
        completion.setMessageKey(metadata.messageKey());
        completion.setMessageArgs(metadata.messageArgs());
        if (instanceMapper.completeIfCurrent(
                        completion, current.getStatus(), permitForTrigger(current.getTriggerId()))
                == 1) {
            return true;
        }
        // MySQL repeatable-read snapshots can still show the pre-race state after a lost CAS.
        // Read the committed winner; the conditional write above retains its ownership fence.
        Instance latest = instanceMapper.selectCurrentById(instanceId);
        if (latest == null || !latest.getStatus().isFinished()) {
            throw conflict("Instance state changed concurrently");
        }
        return false;
    }

    private void requireTransition(InstanceStatus current, InstanceStatus next) {
        if (!InstanceStateMachine.canTransition(current, next)) {
            throw conflict("Illegal instance state transition: " + current + " -> " + next);
        }
    }

    private BusinessException conflict(String message) {
        return BusinessException.localized(ResultCode.CONFLICT, message);
    }

    private void validateParentAndNode(Instance parent, JobExecutionNode node) {
        if (parent.getTargetType() != TargetType.JOB
                || parent.getDefinitionId() == null
                || parent.getDefinitionId() <= 0
                || node == null
                || node.id() == null
                || node.id() <= 0
                || !parent.getDefinitionId().equals(node.definitionId())
                || node.nodeType() == null
                || node.refId() == null
                || node.refId() <= 0) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Job child snapshot source is invalid");
        }
    }

    private void applyChildSnapshot(Instance parent, JobExecutionNode node, Instance child) {
        if (node.nodeType() == TargetType.TASK) {
            child.setDefinitionId(parent.getDefinitionId());
            child.setTaskDefinitionId(node.refTaskDefinitionId());
            return;
        }
        child.setDefinitionId(node.refDefinitionId());
        child.setTaskDefinitionId(null);
    }

    @Transactional
    public void adoptRoot(Long rootInstanceId, TriggerWorkClaim claim) {
        Instance root = requireInstance(rootInstanceId);
        if (!claim.triggerId().equals(root.getTriggerId())) {
            throw new ExecutionOwnershipLostException(claim.triggerId());
        }
        if (workQueue != null) {
            workQueue.bindRoot(claim, rootInstanceId);
        }
        if (instanceMapper.adoptActiveTree(rootInstanceId, claim.fenceToken(), claim.claimToken())
                        == 0
                && !root.getStatus().isFinished()) {
            throw new ExecutionOwnershipLostException(claim.triggerId());
        }
    }

    @Transactional
    public void adoptRootForRetry(Long rootInstanceId, TriggerWorkClaim claim) {
        Instance root = requireInstance(rootInstanceId);
        if (!claim.triggerId().equals(root.getTriggerId())
                || !rootInstanceId.equals(root.getRootInstanceId())
                || root.getParentInstanceId() != null) {
            throw new ExecutionOwnershipLostException(claim.triggerId());
        }
        if (workQueue != null) {
            workQueue.bindRoot(claim, rootInstanceId);
        }
        if (instanceMapper.adoptTreeForRetry(rootInstanceId, claim.fenceToken(), claim.claimToken())
                == 0) {
            throw new ExecutionOwnershipLostException(claim.triggerId());
        }
    }

    private ExecutionFencePermit permitForInstance(Long instanceId) {
        if (fenceCoordinator == null) {
            return ExecutionFencePermit.unfenced();
        }
        Instance instance = requireInstance(instanceId);
        return permitForTrigger(instance.getTriggerId());
    }

    private ExecutionFencePermit permitForTrigger(Long triggerId) {
        if (fenceCoordinator == null) {
            return ExecutionFencePermit.unfenced();
        }
        return fenceCoordinator.requirePermit(triggerId);
    }

    private TriggerWorkClaim requireClaim(Long triggerId) {
        TriggerWorkClaim claim =
                fenceCoordinator == null ? null : fenceCoordinator.activeClaim(triggerId);
        if (claim == null) {
            throw new ExecutionOwnershipLostException(triggerId);
        }
        return claim;
    }
}
