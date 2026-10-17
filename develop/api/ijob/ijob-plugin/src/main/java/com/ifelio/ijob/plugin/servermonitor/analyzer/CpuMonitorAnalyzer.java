package com.ifelio.ijob.plugin.servermonitor.analyzer;

import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifelio.ijob.plugin.servermonitor.MonitorItemType;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifelio.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

@Component
public class CpuMonitorAnalyzer extends AbstractMonitorAnalyzer {
    public CpuMonitorAnalyzer(ThresholdEvaluator evaluator) {
        super(evaluator);
    }

    @Override
    public MonitorItemType type() {
        return MonitorItemType.CPU;
    }

    @Override
    public void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result) {
        CpuMonitorRule rule = config.getRules() == null ? null : config.getRules().getCpu();
        if (report.getCpu() == null || rule == null || !rule.isEnabled()) {
            return;
        }

        Double usage = report.getCpu().getUsage();
        Threshold threshold = rule.getUsage();
        addResult(result, type(), "CPU", usage, threshold);
    }
}
