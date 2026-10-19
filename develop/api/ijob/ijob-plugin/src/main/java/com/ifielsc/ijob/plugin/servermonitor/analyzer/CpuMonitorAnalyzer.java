// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

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
