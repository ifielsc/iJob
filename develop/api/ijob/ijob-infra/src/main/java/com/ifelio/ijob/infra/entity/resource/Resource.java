package com.ifelio.ijob.infra.entity.resource;

import com.ifelio.ijob.contract.enums.ResourceType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Resource {
    @AutoId(IdNamespace.RESOURCE)
    private Long id;

    private String code;
    private String name;
    private ResourceType type;
    private String config;
    private Boolean enabled;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
