// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckItem;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.Map;

class VariableMonitorHandlerTest {
    @Test
    void shouldPublishBoundedHealthSummaryWithoutCheckDetails() {
        MonitorCheckResult result = new MonitorCheckResult();
        result.add(
                MonitorCheckItem.builder()
                        .type(MonitorItemType.CPU)
                        .name("cpu")
                        .current(95D)
                        .threshold(90D)
                        .level(AlarmLevel.CRITICAL)
                        .message("cpu overloaded")
                        .build());
        TaskContext context =
                new TaskContext(
                        1L, new ExecutionHandle(1L), null, new RecordingInstanceLogs(), null);

        new VariableMonitorHandler().handle(result, MonitorConfig.builder().build(), context);

        assertEquals(false, context.getVariable("monitor.healthy"));
        assertEquals(1, context.getVariable("monitor.alarmCount"));
        assertEquals(0, context.getVariable("monitor.warningCount"));
        assertEquals(1, context.getVariable("monitor.criticalCount"));
        assertEquals("CRITICAL", context.getVariable("monitor.maxLevel"));
        Map<?, ?> summary = assertInstanceOf(Map.class, context.getVariable("monitor.result"));
        assertFalse(summary.containsKey("items"));
        assertEquals(5, summary.size());
    }
}
