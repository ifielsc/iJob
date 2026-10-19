// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorCheckItem {
    private MonitorItemType type;
    private String name;
    private Object current;
    private Object threshold;
    private AlarmLevel level;
    private String message;
    private String messageKey;
    private Object[] messageArgs;
}
