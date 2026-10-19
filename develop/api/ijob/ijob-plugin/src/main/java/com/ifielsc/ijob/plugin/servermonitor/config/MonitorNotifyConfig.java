// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorNotifyConfig {
    private boolean enabled;

    private Long notifyConfigId;

    /** Whether to send a recovery notification. */
    private boolean recovery;
}
