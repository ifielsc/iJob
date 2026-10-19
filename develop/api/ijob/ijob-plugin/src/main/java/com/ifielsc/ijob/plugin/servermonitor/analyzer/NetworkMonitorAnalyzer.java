// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.contract.monitor.metric.NetworkMetric;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifielsc.ijob.plugin.servermonitor.rule.NetworkMonitorRule;

import org.springframework.stereotype.Component;

@Component
public class NetworkMonitorAnalyzer extends AbstractMonitorAnalyzer {
    public NetworkMonitorAnalyzer(ThresholdEvaluator evaluator) {
        super(evaluator);
    }

    @Override
    public MonitorItemType type() {
        return MonitorItemType.NETWORK;
    }

    @Override
    public void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result) {
        NetworkMonitorRule rule = config.getRules() == null ? null : config.getRules().getNetwork();
        if (report.getNetworks() == null || rule == null || !rule.isEnabled()) {
            return;
        }

        for (NetworkMetric network : report.getNetworks()) {
            addResult(
                    result,
                    type(),
                    network.getName() + " receive",
                    network.getReceiveRate(),
                    rule.getReceiveRate());
            addResult(
                    result,
                    type(),
                    network.getName() + " send",
                    network.getSendRate(),
                    rule.getSendRate());
        }
    }
}
