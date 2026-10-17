package com.ifelio.ijob.infra.mapper.task;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString(exclude = "pluginConfig")
public class TaskPublicationIdentityInsert {
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
