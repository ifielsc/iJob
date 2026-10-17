package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.core.execute.task.TaskResult;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.notify.InstanceNotifyService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InstanceRuntimeService {
    private final InstanceService instanceService;
    private final InstanceNotifyService instanceNotifyService;

    @Transactional
    public void completeTask(Long instanceId, TaskResult result) {
        if (instanceService.completeTask(
                instanceId, result.getStatus(), result.getMessage(), result.getMessageMetadata())) {
            instanceNotifyService.notifyIfNecessary(instanceId);
        }
    }

    public void running(Long instanceId) {
        instanceService.running(instanceId);
    }

    public void waitingPermit(Long instanceId, java.time.LocalDateTime deadline) {
        instanceService.waitingPermit(instanceId, deadline);
    }

    @Transactional
    public void success(Long instanceId) {
        instanceService.success(instanceId);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    @Transactional
    public void fail(Long instanceId, String message) {
        instanceService.fail(instanceId, message);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    @Transactional
    public void stop(Long instanceId) {
        instanceService.stop(instanceId);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    @Transactional
    public void timeout(Long instanceId, String message) {
        instanceService.timeout(instanceId, message);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    @Transactional
    public void permitWaitTimeout(Long instanceId, String message) {
        instanceService.permitWaitTimeout(instanceId, message);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    @Transactional
    public void skipped(Long instanceId) {
        instanceService.skipped(instanceId);
        instanceNotifyService.notifyIfNecessary(instanceId);
    }

    public void waitingEvent(Long instanceId) {
        instanceService.waitingEvent(instanceId);
    }
}
