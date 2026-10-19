// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.notify.NotificationService;
import com.ifielsc.ijob.core.notify.NotifyDispatchContext;
import com.ifielsc.ijob.core.notify.NotifyDispatchResult;
import com.ifielsc.ijob.core.notify.NotifyDispatchStatus;
import com.ifielsc.ijob.core.notify.NotifyMessage;
import com.ifielsc.ijob.core.notify.template.NotificationMessages;
import com.ifielsc.ijob.core.notify.template.PluginNotification;
import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckItem;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorNotifyConfig;
import com.ifielsc.ijob.plugin.servermonitor.notify.MonitorMessageBuilder;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifyMonitorHandlerTest {
    @Test
    void alarmShouldCarryMonitorSourceAndInstanceId() {
        RecordingNotificationService notifications = new RecordingNotificationService();
        NotifyMonitorHandler handler =
                new NotifyMonitorHandler(
                        notifications,
                        new MonitorMessageBuilder(
                                new NotificationMessages(new AppProperties()),
                                new PluginNotification(
                                        new NotificationMessages(new AppProperties()),
                                        null,
                                        null)));
        MonitorCheckResult result =
                MonitorCheckResult.builder()
                        .items(
                                List.of(
                                        MonitorCheckItem.builder()
                                                .name("cpu")
                                                .level(AlarmLevel.WARNING)
                                                .build()))
                        .build();
        MonitorConfig config =
                MonitorConfig.builder()
                        .notify(
                                MonitorNotifyConfig.builder()
                                        .enabled(true)
                                        .notifyConfigId(92L)
                                        .build())
                        .build();
        TaskContext taskContext =
                new TaskContext(91L, null, null, new RecordingInstanceLogs(), null);

        handler.handle(result, config, taskContext);

        assertEquals(92L, notifications.notifyConfigId);
        assertNotNull(notifications.message);
        assertEquals(NotifySendSource.MONITOR, notifications.context.getSourceType());
        assertEquals(91L, notifications.context.getInstanceId());
    }

    private static final class RecordingNotificationService extends NotificationService {
        private Long notifyConfigId;
        private NotifyMessage message;
        private NotifyDispatchContext context;

        private RecordingNotificationService() {
            super(null, null, null);
        }

        @Override
        public NotifyDispatchResult send(
                Long configId, NotifyMessage notifyMessage, NotifyDispatchContext dispatchContext) {
            notifyConfigId = configId;
            message = notifyMessage;
            context = dispatchContext;
            return new NotifyDispatchResult(NotifyDispatchStatus.DELIVERED, null);
        }
    }
}
