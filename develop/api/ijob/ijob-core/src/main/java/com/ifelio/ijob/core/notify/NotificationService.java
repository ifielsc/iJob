package com.ifelio.ijob.core.notify;

import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.core.notify.template.JobNotifyTemplate;
import com.ifelio.ijob.core.notify.template.TaskNotifyTemplate;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationOutboxService outboxService;
    private final TaskNotifyTemplate taskTemplate;
    private final JobNotifyTemplate jobTemplate;

    public NotificationService(
            NotificationOutboxService outboxService,
            TaskNotifyTemplate taskTemplate,
            JobNotifyTemplate jobTemplate) {
        this.outboxService = outboxService;
        this.taskTemplate = taskTemplate;
        this.jobTemplate = jobTemplate;
    }

    public NotifyDispatchResult send(Long notifyConfigId, NotificationContext context) {
        return send(notifyConfigId, context, null);
    }

    public NotifyDispatchResult send(
            Long notifyConfigId, NotificationContext context, Long notifyBindingId) {
        if (context == null || context.getTargetType() == null) {
            return new NotifyDispatchResult(NotifyDispatchStatus.DELIVERY_FAILED, null);
        }
        NotifyMessage message = buildMessage(context);
        NotifyDispatchContext dispatchContext =
                NotifyDispatchContext.builder()
                        .sourceType(NotifySendSource.INSTANCE)
                        .targetType(context.getTargetType())
                        .targetId(context.getTargetId())
                        .instanceId(context.getInstanceId())
                        .notifyBindingId(notifyBindingId)
                        .build();
        return enqueue(notifyConfigId, message, dispatchContext);
    }

    private NotifyMessage buildMessage(NotificationContext context) {
        return context.getTargetType() == TargetType.TASK
                ? taskTemplate.build(context)
                : jobTemplate.build(context);
    }

    public NotifyDispatchResult send(Long notifyConfigId, NotifyMessage message) {
        return send(notifyConfigId, message, NotifyDispatchContext.system());
    }

    public NotifyDispatchResult send(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        return enqueue(notifyConfigId, message, context);
    }

    private NotifyDispatchResult enqueue(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        outboxService.enqueue(notifyConfigId, message, context);
        return new NotifyDispatchResult(NotifyDispatchStatus.QUEUED, null);
    }
}
