package com.ifelio.ijob.plugin.servermonitor.config;

import com.ifelio.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.MemoryMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.NetworkMonitorRule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorRuleConfig {
    private CpuMonitorRule cpu;

    private MemoryMonitorRule memory;

    private DiskMonitorRule disk;

    private NetworkMonitorRule network;

    private List<ApplicationMonitorRule> applications;
}
