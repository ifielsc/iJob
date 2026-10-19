// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataCheckConfig {
    /** Database resource ID. */
    private Long resourceId;

    private List<DataCheckItemConfig> checks = new ArrayList<>();

    /** Timeout in seconds for each check query. */
    private Integer queryTimeoutSeconds = 30;

    private List<HandlerConfig> handlers = new ArrayList<>();
}
