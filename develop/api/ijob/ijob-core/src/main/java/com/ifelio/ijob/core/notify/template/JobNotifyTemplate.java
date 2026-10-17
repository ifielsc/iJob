package com.ifelio.ijob.core.notify.template;

import com.ifelio.ijob.core.notify.NotificationContext;
import com.ifelio.ijob.core.notify.NotifyMessage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobNotifyTemplate implements NotifyTemplate {
    private final NotificationMessages messages;

    @Override
    public NotifyMessage build(NotificationContext context) {
        return ExecutionNotification.render(messages, "job", context);
    }
}
