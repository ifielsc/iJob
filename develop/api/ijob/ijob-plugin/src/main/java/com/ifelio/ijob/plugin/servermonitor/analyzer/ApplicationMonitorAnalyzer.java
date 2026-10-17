package com.ifelio.ijob.plugin.servermonitor.analyzer;

import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;
import com.ifelio.ijob.plugin.servermonitor.AlarmLevel;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckItem;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifelio.ijob.plugin.servermonitor.MonitorItemType;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifelio.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ApplicationMonitorAnalyzer extends AbstractMonitorAnalyzer {
    public ApplicationMonitorAnalyzer(ThresholdEvaluator evaluator) {
        super(evaluator);
    }

    @Override
    public MonitorItemType type() {
        return MonitorItemType.APPLICATION;
    }

    @Override
    public void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result) {
        if (report.getApplications() == null
                || config.getRules() == null
                || config.getRules().getApplications() == null) {
            return;
        }

        Map<String, ApplicationMonitorRule> rules =
                config.getRules().getApplications().stream()
                        .collect(Collectors.toMap(ApplicationMonitorRule::getName, rule -> rule));

        Map<String, ApplicationCollectConfig> collections =
                config.getCollect() == null || config.getCollect().getApplications() == null
                        ? Map.of()
                        : config.getCollect().getApplications().stream()
                                .collect(
                                        Collectors.toMap(
                                                ApplicationCollectConfig::getName,
                                                application -> application));

        for (ApplicationMetric app : report.getApplications()) {
            ApplicationMonitorRule rule = rules.get(app.getName());
            ApplicationCollectConfig collection = collections.get(app.getName());

            if (rule == null || !rule.isEnabled() || collection == null) {
                continue;
            }

            if (Boolean.TRUE.equals(collection.getRequired()) && !app.isFound()) {
                result.add(
                        MonitorCheckItem.builder()
                                .type(type())
                                .name(app.getName())
                                .level(AlarmLevel.CRITICAL)
                                .message("Application is not running")
                                .messageKey("monitor.application.not-running")
                                .build());
                continue;
            }

            if (!app.isFound()) {
                continue;
            }

            if (!Boolean.TRUE.equals(collection.getAllowMultiple())
                    && app.getProcessCount() != null
                    && app.getProcessCount() > 1) {
                result.add(
                        MonitorCheckItem.builder()
                                .type(type())
                                .name(app.getName())
                                .current(app.getProcessCount())
                                .threshold(1)
                                .level(AlarmLevel.CRITICAL)
                                .message("Application matches multiple processes")
                                .messageKey("monitor.application.multiple-processes")
                                .build());
                continue;
            }

            /*
             * CPU
             */
            addResult(result, type(), app.getName() + " CPU", app.getCpuUsage(), rule.getCpu());

            /*
             * Memory
             */
            addResult(
                    result,
                    type(),
                    app.getName() + " Memory",
                    bytesToMB(app.getMemoryUsage()),
                    rule.getMemory());
        }
    }

    private double bytesToMB(Long bytes) {
        if (bytes == null) {
            return 0;
        }
        return bytes / 1024D / 1024D;
    }
}
