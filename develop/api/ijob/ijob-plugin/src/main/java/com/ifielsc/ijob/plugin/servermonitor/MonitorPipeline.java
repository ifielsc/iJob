// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.servermonitor.analyzer.CompositeMonitorAnalyzer;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorCollectConfig;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.handler.CompositeMonitorHandler;
import com.ifielsc.ijob.plugin.servermonitor.provider.MonitorDataProvider;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MonitorPipeline {
    private final MonitorDataProvider provider;
    private final CompositeMonitorAnalyzer compositeAnalyzer;
    private final CompositeMonitorHandler compositeHandler;
    private final MonitorReportValidator reportValidator;

    public MonitorCheckResult execute(MonitorConfig config, TaskContext context) {
        checkpoint(context);
        context.progress(5);
        MonitorCollectRequest request = buildRequest(config);
        context.logInfo("Monitor collection started, resourceId=" + config.getResourceId());
        context.progress(10);
        MonitorReport report = provider.collect(config.getResourceId(), request, context);
        checkpoint(context);
        context.progress(60);
        reportValidator.validate(report, request, config);
        context.logInfo("Monitor collection completed and report validated");
        checkpoint(context);
        context.progress(70);

        MonitorCheckResult result = compositeAnalyzer.analyze(report, config);
        result.setCollectTime(report.getCollectTime());
        context.logInfo(
                "Monitor analysis completed, items="
                        + result.getItems().size()
                        + ", alarms="
                        + result.getItems().stream()
                                .filter(item -> item.getLevel() != AlarmLevel.NORMAL)
                                .count());
        checkpoint(context);
        context.progress(85);
        compositeHandler.handle(result, config, context);
        context.logInfo("Monitor result handlers completed");
        checkpoint(context);
        context.progress(95);
        return result;
    }

    private void checkpoint(TaskContext context) {
        context.checkStop();
        context.checkInterrupted();
        context.checkPause();
    }

    private MonitorCollectRequest buildRequest(MonitorConfig config) {
        if (config == null || config.getCollect() == null) {
            throw new IllegalArgumentException("monitor collect config is required");
        }

        MonitorCollectConfig collect = config.getCollect();
        List<CollectType> items =
                collect.getItems() == null ? List.of() : List.copyOf(collect.getItems());
        List<ApplicationCollectConfig> applications =
                collect.getApplications() == null
                        ? List.of()
                        : List.copyOf(collect.getApplications());
        return MonitorCollectRequest.builder()
                .collectItems(items)
                .applications(applications)
                .build();
    }
}
