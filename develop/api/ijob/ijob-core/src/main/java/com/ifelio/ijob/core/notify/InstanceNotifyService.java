package com.ifelio.ijob.core.notify;

import cn.hutool.json.JSONUtil;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.NotifyPolicy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.instance.InstanceService;
import com.ifelio.ijob.core.job.definition.JobExecutionSpec;
import com.ifelio.ijob.core.runtime.definition.RuntimeDefinitionService;
import com.ifelio.ijob.core.task.definition.TaskAdmissionSnapshot;
import com.ifelio.ijob.infra.entity.notify.NotifyBinding;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstanceNotifyService {
    private final InstanceService instanceService;
    private final RuntimeDefinitionService runtimeDefinitionService;
    private final NotificationService notificationService;
    private final NotifyBindingService notifyBindingService;

    public void notifyIfNecessary(Long instanceId) {
        Instance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            return;
        }

        if (!instance.isRoot()) {
            return;
        }

        if (!instance.getStatus().isFinished()) {
            return;
        }

        switch (instance.getTargetType()) {
            case TASK -> notifyTask(instance);
            case JOB -> notifyJob(instance);
        }
    }

    private void notifyTask(Instance instance) {
        TaskAdmissionSnapshot snapshot =
                runtimeDefinitionService.loadTask(
                        instance.getTargetId(), instance.getTaskDefinitionId());
        NotificationContext context = buildContext(snapshot.executionSpec().getName(), instance);
        List<NotifyBinding> bindings =
                notifyBindingService.listNotifyBindingsByTargetId(
                        TargetType.TASK, snapshot.taskId());
        for (NotifyBinding binding : bindings) {
            if (!allowNotify(binding.getNotifyPolicy(), instance.getStatus())) {
                continue;
            }

            notificationService.send(binding.getNotifyConfigId(), context, binding.getId());
        }
    }

    private void notifyJob(Instance instance) {
        JobExecutionSpec spec =
                runtimeDefinitionService.loadJob(
                        instance.getTargetId(), instance.getDefinitionId());
        NotificationContext context = buildContext(spec.name(), instance);
        List<NotifyBinding> bindings =
                notifyBindingService.listNotifyBindingsByTargetId(TargetType.JOB, spec.jobId());
        for (NotifyBinding binding : bindings) {
            if (!allowNotify(binding.getNotifyPolicy(), instance.getStatus())) {
                continue;
            }

            notificationService.send(binding.getNotifyConfigId(), context, binding.getId());
        }
    }

    private boolean allowNotify(NotifyPolicy policy, InstanceStatus status) {
        return switch (policy) {
            case NEVER -> false;
            case ALWAYS -> true;
            case ON_SUCCESS -> status == InstanceStatus.SUCCESS;
            case ON_FAILURE ->
                    status == InstanceStatus.FAILED || status == InstanceStatus.TIMED_OUT;
        };
    }

    private NotificationContext buildContext(String targetName, Instance instance) {
        Long duration = null;
        if (instance.getStartTime() != null && instance.getEndTime() != null) {
            duration = Duration.between(instance.getStartTime(), instance.getEndTime()).toMillis();
        }

        return NotificationContext.builder()
                .instanceId(instance.getId())
                .targetType(instance.getTargetType())
                .targetId(instance.getTargetId())
                .targetName(targetName)
                .status(instance.getStatus())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .duration(duration)
                .retryCount(instance.getRetryCount())
                .message(instance.getMessage())
                .messageKey(instance.getMessageKey())
                .messageArgs(messageArguments(instance))
                .build();
    }

    private Object[] messageArguments(Instance instance) {
        if (instance.getMessageArgs() == null) {
            return new Object[0];
        }
        try {
            return JSONUtil.parseArray(instance.getMessageArgs()).toArray();
        } catch (RuntimeException exception) {
            log.warn("Invalid stored message arguments, instanceId={}", instance.getId());
            return new Object[0];
        }
    }
}
