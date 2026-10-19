// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.metric.DiskMetric;
import com.ifielsc.ijob.plugin.servermonitor.DiskPathNormalizer;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

@Component
public class DiskMonitorAnalyzer extends AbstractMonitorAnalyzer {
    public DiskMonitorAnalyzer(ThresholdEvaluator evaluator) {
        super(evaluator);
    }

    @Override
    public MonitorItemType type() {
        return MonitorItemType.DISK;
    }

    @Override
    public void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result) {
        DiskMonitorRule rule = config.getRules() == null ? null : config.getRules().getDisk();
        if (report.getDisks() == null || rule == null || !rule.isEnabled()) {
            return;
        }

        for (DiskMetric disk : report.getDisks()) {
            Threshold threshold = resolveThreshold(disk.getPath(), rule);

            if (threshold == null) {
                throw new IllegalStateException("disk threshold is not configured");
            }

            addResult(result, type(), disk.getPath(), disk.getUsage(), threshold);
        }
    }

    private Threshold resolveThreshold(String path, DiskMonitorRule rule) {
        String normalizedPath = DiskPathNormalizer.normalize(path);
        if (rule.getPaths() != null) {
            for (var entry : rule.getPaths().entrySet()) {
                if (normalizedPath.equals(DiskPathNormalizer.normalize(entry.getKey()))) {
                    return entry.getValue();
                }
            }
        }
        return rule.getUsage();
    }
}
