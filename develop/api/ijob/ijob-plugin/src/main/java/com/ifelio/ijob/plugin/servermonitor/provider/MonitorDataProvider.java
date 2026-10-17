package com.ifelio.ijob.plugin.servermonitor.provider;

import com.ifelio.ijob.contract.monitor.MonitorCollectRequest;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.core.execute.task.TaskContext;

public interface MonitorDataProvider {
    MonitorReport collect(Long resourceId, MonitorCollectRequest request, TaskContext context);
}
