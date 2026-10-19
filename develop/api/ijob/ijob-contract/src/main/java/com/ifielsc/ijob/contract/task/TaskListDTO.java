package com.ifielsc.ijob.contract.task;

import com.ifielsc.ijob.contract.enums.PluginType;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskListDTO {
    private Long id;
    private String name;
    private PluginType pluginType;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private List<String> groups;
    private Long currentTaskDefinitionId;
    private Long currentRevision;
    private LocalDateTime updateTime;
}
