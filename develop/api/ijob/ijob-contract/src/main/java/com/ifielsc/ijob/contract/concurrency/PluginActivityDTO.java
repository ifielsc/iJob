// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.concurrency;

import com.ifielsc.ijob.contract.enums.PluginType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PluginActivityDTO {
    private PluginType type;
    private Integer activeInstanceCount;
}
