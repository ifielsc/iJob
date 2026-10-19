// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.config;

import com.ifielsc.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.CpuMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.MemoryMonitorRule;
import com.ifielsc.ijob.plugin.servermonitor.rule.NetworkMonitorRule;

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
