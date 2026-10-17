package com.ifelio.ijob.plugin.servermonitor.analyzer;

import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CompositeMonitorAnalyzer {
    private final List<MonitorAnalyzer> analyzers;

    public MonitorCheckResult analyze(MonitorReport report, MonitorConfig config) {
        MonitorCheckResult result = new MonitorCheckResult();
        for (MonitorAnalyzer analyzer : analyzers) {
            analyzer.analyze(report, config, result);
        }
        return result;
    }
}
