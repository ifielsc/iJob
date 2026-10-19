// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.task;

import com.ifielsc.ijob.contract.enums.PluginType;

import lombok.Data;

import java.time.LocalDateTime;

/** Public version metadata; never contains plugin configuration. */
@Data
public class TaskVersionDTO {
    private Long taskId;
    private Long definitionId;
    private Long revision;
    private String name;
    private PluginType pluginType;
    private Boolean enabled;
    private LocalDateTime publishedAt;
    private boolean current;
}
