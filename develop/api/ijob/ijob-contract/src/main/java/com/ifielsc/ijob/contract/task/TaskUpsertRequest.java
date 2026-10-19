// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.task;

import com.ifielsc.ijob.contract.enums.PluginType;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString(exclude = "pluginConfig")
public class TaskUpsertRequest {
    private String name;
    private PluginType pluginType;
    private String pluginConfig;
    private List<String> preservedHeaderNames;
    private List<String> preservedRemoteStopHeaderNames;
    private Long headerSourceDefinitionId;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private List<String> groups;
}
