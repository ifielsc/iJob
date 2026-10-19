package com.ifielsc.ijob.plugin.servermonitor.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorConfig {
    /** Monitor Agent resource ID. */
    private Long resourceId;

    private MonitorCollectConfig collect;

    private MonitorRuleConfig rules;

    private MonitorNotifyConfig notify;
}
