// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.config;

import lombok.Data;

import java.util.Map;

@Data
public class HandlerConfig {
    private String type;

    private Map<String, Object> params;
}
