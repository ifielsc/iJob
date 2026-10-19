// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.rule;

import lombok.Data;

@Data
public abstract class AbstractMonitorRule {
    private boolean enabled = true;
}
