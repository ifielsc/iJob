package com.ifelio.ijob.infra.entity.task;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Task {
    @AutoId(IdNamespace.TASK)
    private Long id;

    private String name;
    private PluginType pluginType;
    private String pluginConfig;
    private Integer retryTimes;
    private Integer retryInterval;
    private Integer timeout;
    private Boolean enabled;
    private String description;

    private Long currentTaskDefinitionId;
    private Long currentRevision;
    private Boolean retired;
    private LocalDateTime retiredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
