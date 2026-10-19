package com.ifielsc.ijob.plugin.servermonitor.config;

import com.ifielsc.ijob.contract.monitor.CollectType;
import com.ifielsc.ijob.contract.monitor.application.ApplicationCollectConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorCollectConfig {
    @Builder.Default private List<CollectType> items = new ArrayList<>();

    @Builder.Default private List<ApplicationCollectConfig> applications = new ArrayList<>();
}
