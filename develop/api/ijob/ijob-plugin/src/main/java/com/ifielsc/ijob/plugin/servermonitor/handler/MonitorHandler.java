package com.ifielsc.ijob.plugin.servermonitor.handler;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;

public interface MonitorHandler {
    boolean support(MonitorConfig config);

    void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context);
}
