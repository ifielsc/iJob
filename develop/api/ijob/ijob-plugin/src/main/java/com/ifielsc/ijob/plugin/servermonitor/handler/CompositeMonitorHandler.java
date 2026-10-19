// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.handler;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompositeMonitorHandler {
    private final List<MonitorHandler> handlers;

    public void handle(MonitorCheckResult result, MonitorConfig config, TaskContext context) {
        for (MonitorHandler handler : handlers) {
            checkpoint(context);
            if (handler.support(config)) {
                handler.handle(result, config, context);
            }
        }
        checkpoint(context);
    }

    private void checkpoint(TaskContext context) {
        context.checkStop();
        context.checkInterrupted();
        context.checkPause();
    }
}
