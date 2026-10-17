package com.ifelio.ijob.plugin.servermonitor.handler;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;

public interface MonitorHandler {
    boolean support(MonitorConfig config);

    void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context);
}
