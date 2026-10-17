package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString(exclude = "pluginConfig")
public class TaskCurrentDefinitionAdvance {
    private Long taskId;
    private Long expectedDefinitionId;
    private Long expectedRevision;
    private Long definitionId;
    private Long nextRevision;
    private String name;
    private PluginType pluginType;
    private String pluginConfig;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private LocalDateTime updateTime;
}
