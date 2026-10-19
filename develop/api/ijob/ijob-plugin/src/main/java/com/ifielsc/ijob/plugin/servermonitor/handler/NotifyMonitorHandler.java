package com.ifielsc.ijob.plugin.servermonitor.handler;

import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.notify.NotificationService;
import com.ifielsc.ijob.core.notify.NotifyDispatchContext;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.notify.MonitorMessageBuilder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotifyMonitorHandler implements MonitorHandler {
    private final NotificationService notificationService;
    private final MonitorMessageBuilder builder;

    @Override
    public boolean support(MonitorConfig config) {
        return config.getNotify() != null && config.getNotify().isEnabled();
    }

    @Override
    public void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context) {
        if (!result.hasAlarm()) {
            return;
        }

        NotifyMessage message = builder.build(result, config, context);
        notificationService.send(
                config.getNotify().getNotifyConfigId(),
                message,
                NotifyDispatchContext.builder()
                        .sourceType(NotifySendSource.MONITOR)
                        .instanceId(context.getInstanceId())
                        .build());
    }
}
