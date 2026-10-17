package com.ifelio.ijob.infra.entity.task;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TaskDefinition {
    @AutoId(IdNamespace.TASK_DEFINITION)
    private Long id;

    private Long taskId;
    private Long revision;
    private String name;
    private PluginType pluginType;
    private String pluginConfig;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;
    private LocalDateTime createTime;
}
