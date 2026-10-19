// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.task;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.contract.notify.NotifyBindingDTO;
import com.ifielsc.ijob.contract.schedule.ScheduleDTO;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@ToString(exclude = "pluginConfig")
public class TaskDetailDTO {
    private Long id;
    private String name;
    private PluginType pluginType;
    private String pluginConfig;
    private List<String> headerNames;
    private List<String> remoteStopHeaderNames;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private List<String> groups;
    private Long currentTaskDefinitionId;
    private Long currentRevision;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<ScheduleDTO> schedules;
    private List<NotifyBindingDTO> notifyBindings;
}
