// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.servermonitor.rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationMonitorRule extends AbstractMonitorRule {
    private String name;

    private Boolean required;

    /** CPU */
    private Threshold cpu;

    private Threshold memory;
}
