package com.ifelio.ijob.plugin.servermonitor.analyzer;

import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifelio.ijob.plugin.servermonitor.MonitorItemType;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;

public interface MonitorAnalyzer {
    MonitorItemType type();

    void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result);
}
