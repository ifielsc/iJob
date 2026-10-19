package com.ifielsc.ijob.plugin.servermonitor.handler;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.servermonitor.AlarmLevel;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class VariableMonitorHandler implements MonitorHandler {
    @Override
    public boolean support(MonitorConfig config) {
        return true;
    }

    @Override
    public void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context) {
        boolean healthy = !result.hasAlarm();
        int alarmCount = result.alarmCount();
        int warningCount = result.count(AlarmLevel.WARNING);
        int criticalCount = result.count(AlarmLevel.CRITICAL);
        String maxLevel = result.maxLevel().name();

        context.putVariable("monitor.success", healthy);
        context.putVariable("monitor.healthy", healthy);
        context.putVariable("monitor.alarmCount", alarmCount);
        context.putVariable("monitor.warningCount", warningCount);
        context.putVariable("monitor.criticalCount", criticalCount);
        context.putVariable("monitor.maxLevel", maxLevel);
        context.putVariable(
                "monitor.result",
                summary(healthy, alarmCount, warningCount, criticalCount, maxLevel));
    }

    private Map<String, Object> summary(
            boolean healthy, int alarmCount, int warningCount, int criticalCount, String maxLevel) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("healthy", healthy);
        summary.put("alarmCount", alarmCount);
        summary.put("warningCount", warningCount);
        summary.put("criticalCount", criticalCount);
        summary.put("maxLevel", maxLevel);
        return Collections.unmodifiableMap(summary);
    }
}
