// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.provider;

import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.core.execute.task.TaskContext;

public interface MonitorDataProvider {
    MonitorReport collect(Long resourceId, MonitorCollectRequest request, TaskContext context);
}
