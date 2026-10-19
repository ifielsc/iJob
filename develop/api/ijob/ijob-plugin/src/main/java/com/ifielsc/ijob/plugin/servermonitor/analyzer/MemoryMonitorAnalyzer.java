// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.MemoryMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

@Component
public class MemoryMonitorAnalyzer extends AbstractMonitorAnalyzer {
    public MemoryMonitorAnalyzer(ThresholdEvaluator evaluator) {
        super(evaluator);
    }

    @Override
    public MonitorItemType type() {
        return MonitorItemType.MEMORY;
    }

    @Override
    public void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result) {
        MemoryMonitorRule rule = config.getRules() == null ? null : config.getRules().getMemory();
        if (report.getMemory() == null || rule == null || !rule.isEnabled()) {
            return;
        }

        Threshold threshold = rule.getUsage();
        addResult(result, type(), "Memory", report.getMemory().getUsage(), threshold);
    }
}
