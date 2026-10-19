// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.enums.EventWaitType;
import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.ifielsc.ijob.core.execute.InstanceRuntimeService;
import com.ifielsc.ijob.core.execute.task.TaskResult;
import com.ifielsc.ijob.core.instance.InstanceService;
import com.ifielsc.ijob.core.trigger.TriggerRuntimeService;
import com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator;
import com.ifielsc.ijob.infra.coordination.work.SuspendedWorkPermit;
import com.ifielsc.ijob.infra.coordination.work.TriggerWorkStatus;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.entity.runtime.EventWait;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventWaitResolutionService {
    private final EventWaitMapper eventWaitMapper;
    private final InstanceService instanceService;
    private final InstanceRuntimeService instanceRuntimeService;
    private final TriggerRuntimeService triggerRuntimeService;
    private final InstanceContinuationDispatcher continuationDispatcher;
    private final EventWaitQueryService eventWaitQueryService;
    private final ExecutionFenceCoordinator fenceCoordinator;
    private final DatabaseTriggerWorkQueue workQueue;

    @Autowired
    public EventWaitResolutionService(
            EventWaitMapper eventWaitMapper,
            InstanceService instanceService,
            InstanceRuntimeService instanceRuntimeService,
            TriggerRuntimeService triggerRuntimeService,
            InstanceContinuationDispatcher continuationDispatcher,
            EventWaitQueryService eventWaitQueryService,
            ExecutionFenceCoordinator fenceCoordinator,
            DatabaseTriggerWorkQueue workQueue) {
        this.eventWaitMapper = eventWaitMapper;
        this.instanceService = instanceService;
        this.instanceRuntimeService = instanceRuntimeService;
        this.triggerRuntimeService = triggerRuntimeService;
        this.continuationDispatcher = continuationDispatcher;
        this.eventWaitQueryService = eventWaitQueryService;
        this.fenceCoordinator = fenceCoordinator;
        this.workQueue = workQueue;
    }

    public EventWaitResolutionService(
            EventWaitMapper eventWaitMapper,
            InstanceService instanceService,
            InstanceRuntimeService instanceRuntimeService,
            TriggerRuntimeService triggerRuntimeService,
            InstanceContinuationDispatcher continuationDispatcher,
            EventWaitQueryService eventWaitQueryService) {
        this(
                eventWaitMapper,
                instanceService,
                instanceRuntimeService,
                triggerRuntimeService,
                continuationDispatcher,
                eventWaitQueryService,
                null,
                null);
    }

    @Transactional
    public int match(EventRecord event) {
        List<EventWait> waits =
                eventWaitMapper.selectWaitingForEvent(
                        event.getEventCode(), event.getBusinessKey(),
                        event.getCorrelationKey(), event.getArrivedAt());
        int matched = 0;
        for (EventWait wait : waits) {
            if (resolve(wait, EventWaitStatus.MATCHED, event.getId(), null, null)) {
                matched++;
            }
        }
        return matched;
    }

    @Transactional
    public EventWait decide(
            Long waitId, EventWaitStatus decision, String operatorId, String comment) {
        EventWait wait = eventWaitMapper.selectById(waitId);
        if (wait == null) {
            throw BusinessException.localized(ResultCode.NOT_FOUND, "Event wait not found");
        }
        if (wait.getWaitType() != EventWaitType.APPROVAL) {
            throw BusinessException.localized(ResultCode.CONFLICT, "Event wait is not an approval");
        }
        if (decision != EventWaitStatus.APPROVED
                && decision != EventWaitStatus.REJECTED
                && decision != EventWaitStatus.WITHDRAWN) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Approval decision is invalid");
        }
        requireOperator(wait, operatorId);
        if (comment != null && comment.length() > 500) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Decision comment cannot exceed 500 characters");
        }
        if (!resolve(wait, decision, null, operatorId, comment)) {
            EventWait latest = eventWaitMapper.selectById(waitId);
            if (latest != null && latest.getStatus() == decision) {
                return latest;
            }
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Event wait has already been resolved");
        }
        return eventWaitMapper.selectById(waitId);
    }

    @Transactional
    public boolean timeout(EventWait wait, LocalDateTime now) {
        return resolve(wait, EventWaitStatus.TIMED_OUT, null, null, "Event wait timed out", now);
    }

    @Transactional
    public void stopRoot(Long rootInstanceId) {
        stopWaits(eventWaitMapper.selectWaitingByRoot(rootInstanceId));
    }

    @Transactional
    public void stopInstance(Long instanceId) {
        stopWaits(eventWaitMapper.selectWaitingByInstance(instanceId));
    }

    private void stopWaits(List<EventWait> waits) {
        LocalDateTime now = LocalDateTime.now();
        for (EventWait wait : waits) {
            if (eventWaitMapper.resolveIfWaiting(
                            wait.getId(),
                            EventWaitStatus.STOPPED,
                            null,
                            null,
                            "Execution stopped",
                            now)
                    == 1) {
                Instance instance = instanceService.getInstance(wait.getInstanceId());
                if (instance != null && instance.getStatus() == InstanceStatus.WAITING_EVENT) {
                    instanceRuntimeService.stop(instance.getId());
                }
            }
        }
    }

    private boolean resolve(
            EventWait wait,
            EventWaitStatus status,
            Long eventId,
            String operatorId,
            String comment) {
        return resolve(wait, status, eventId, operatorId, comment, LocalDateTime.now());
    }

    private boolean resolve(
            EventWait wait,
            EventWaitStatus status,
            Long eventId,
            String operatorId,
            String comment,
            LocalDateTime now) {
        Instance root = instanceService.getInstance(wait.getRootInstanceId());
        if (root == null || root.getTriggerId() == null) {
            return false;
        }
        SuspendedWorkPermit suspendedPermit = null;
        if (fenceCoordinator != null && fenceCoordinator.isEnforced()) {
            var work = workQueue.get(root.getTriggerId());
            if (work == null || work.getStatus() != TriggerWorkStatus.SUSPENDED) {
                return false;
            }
            suspendedPermit = new SuspendedWorkPermit(root.getTriggerId(), work.getFenceToken());
        }
        if (fenceCoordinator == null) {
            return doResolve(wait, status, eventId, operatorId, comment, now, null);
        }
        SuspendedWorkPermit permit = suspendedPermit;
        return fenceCoordinator.withSuspendedAccess(
                permit, () -> doResolve(wait, status, eventId, operatorId, comment, now, permit));
    }

    private boolean doResolve(
            EventWait wait,
            EventWaitStatus status,
            Long eventId,
            String operatorId,
            String comment,
            LocalDateTime now,
            SuspendedWorkPermit suspendedPermit) {
        if (eventWaitMapper.resolveIfWaiting(
                        wait.getId(), status, eventId, operatorId, comment, now)
                != 1) {
            return false;
        }
        completeWaitingInstance(wait, status, comment, suspendedPermit);
        return true;
    }

    private void completeWaitingInstance(
            EventWait wait,
            EventWaitStatus status,
            String comment,
            SuspendedWorkPermit suspendedPermit) {
        Instance instance = instanceService.getInstance(wait.getInstanceId());
        if (instance == null || instance.getStatus() != InstanceStatus.WAITING_EVENT) {
            return;
        }
        switch (status) {
            case MATCHED, APPROVED -> instanceRuntimeService.success(instance.getId());
            case REJECTED ->
                    instanceRuntimeService.completeTask(
                            instance.getId(),
                            comment == null
                                    ? TaskResult.fail(CoreMessage.APPROVAL_REJECTED)
                                    : TaskResult.fail(comment));
            case TIMED_OUT ->
                    instanceRuntimeService.completeTask(
                            instance.getId(), TaskResult.timeout(CoreMessage.EVENT_TIMEOUT));
            case WITHDRAWN, STOPPED -> instanceRuntimeService.stop(instance.getId());
            case WAITING -> throw new IllegalArgumentException("Waiting is not a resolution");
        }

        if (instance.getId().equals(wait.getRootInstanceId())
                && instance.getTargetType() == TargetType.TASK) {
            completeRootTaskTrigger(instanceService.getInstance(instance.getId()));
            if (fenceCoordinator != null
                    && fenceCoordinator.isEnforced()
                    && (suspendedPermit == null || !workQueue.completeSuspended(suspendedPermit))) {
                throw new IllegalStateException("Suspended trigger work could not be completed");
            }
            return;
        }
        continuationDispatcher.dispatchAfterCommit(wait.getRootInstanceId());
    }

    private void completeRootTaskTrigger(Instance instance) {
        if (instance == null || instance.getTriggerId() == null) {
            return;
        }
        switch (instance.getStatus()) {
            case SUCCESS -> triggerRuntimeService.markSuccess(instance.getTriggerId());
            case FAILED ->
                    triggerRuntimeService.markFailed(
                            instance.getTriggerId(), instance.getMessage());
            case TIMED_OUT ->
                    triggerRuntimeService.markTimedOut(
                            instance.getTriggerId(), instance.getMessage());
            case STOPPED ->
                    triggerRuntimeService.markStopped(
                            instance.getTriggerId(), instance.getMessage());
            default -> {}
        }
    }

    private void requireOperator(EventWait wait, String operatorId) {
        eventWaitQueryService.requireApprovalAccess(wait, operatorId);
    }
}
