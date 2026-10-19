package com.ifielsc.ijob.plugin.servermonitor.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.notify.template.NotificationMessages;
import com.ifielsc.ijob.core.notify.template.PluginNotification;
import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckItem;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.notify.MonitorMessageBuilder;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class MonitorNoticeFormatTest {
    @Test
    void rendersUnitsMissingProcessSeverityOrderAndCollectionTime() {
        var messages = new NotificationMessages(new AppProperties());
        var builder =
                new MonitorMessageBuilder(messages, new PluginNotification(messages, null, null));
        var result =
                MonitorCheckResult.builder()
                        .collectTime(LocalDateTime.of(2026, 10, 16, 0, 0))
                        .items(
                                List.of(
                                        MonitorCheckItem.builder()
                                                .name("CPU")
                                                .type(MonitorItemType.CPU)
                                                .current(85.1234)
                                                .threshold(new Threshold(80d, 90d))
                                                .level(AlarmLevel.WARNING)
                                                .build(),
                                        MonitorCheckItem.builder()
                                                .name("Missing process")
                                                .type(MonitorItemType.APPLICATION)
                                                .level(AlarmLevel.CRITICAL)
                                                .messageKey("monitor.application.not-running")
                                                .build(),
                                        MonitorCheckItem.builder()
                                                .name("App Memory")
                                                .type(MonitorItemType.APPLICATION)
                                                .current(512d)
                                                .threshold(new Threshold(300d, 500d))
                                                .level(AlarmLevel.CRITICAL)
                                                .build(),
                                        MonitorCheckItem.builder()
                                                .name("eth receive")
                                                .type(MonitorItemType.NETWORK)
                                                .current(1024d)
                                                .threshold(new Threshold(1000d, 2000d))
                                                .level(AlarmLevel.WARNING)
                                                .build()))
                        .build();
        var notice =
                builder.build(
                        result,
                        MonitorConfig.builder().resourceId(7L).build(),
                        new TaskContext(42L, null, null, null, null));
        String text = notice.getContent();
        assertTrue(text.contains("Not running"));
        assertTrue(text.contains("Monitored server"));
        assertTrue(text.contains("Current state"));
        assertTrue(text.contains("UTC+08:00"));
        assertFalse(text.contains("Data source"));
        assertFalse(text.contains("Actual value"));
        assertTrue(notice.getEmailContent().contains("Monitored server"));
        assertFalse(notice.getEmailContent().contains("Actual value"));
        assertFalse(text.contains("null"));
        assertTrue(text.contains("85.12%"));
        assertTrue(text.contains("≥ 80%"));
        assertTrue(text.contains("512 MiB"));
        assertTrue(text.contains("1024 B/s"));
        assertTrue(text.contains("2026-10-16 08:00:00"));
        assertTrue(text.indexOf("Missing process") < text.indexOf("CPU"));
    }
}
