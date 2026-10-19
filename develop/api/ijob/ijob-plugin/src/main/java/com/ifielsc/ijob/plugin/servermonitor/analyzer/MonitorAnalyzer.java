package com.ifielsc.ijob.plugin.servermonitor.analyzer;

import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.plugin.servermonitor.MonitorCheckResult;
import com.ifielsc.ijob.plugin.servermonitor.MonitorItemType;
import com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig;

public interface MonitorAnalyzer {
    MonitorItemType type();

    void analyze(MonitorReport report, MonitorConfig config, MonitorCheckResult result);
}
