package com.ifelio.ijob.infra.entity.event;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EventBinding {
    @AutoId(IdNamespace.EVENT_BINDING)
    private Long id;

    private TargetType targetType;
    private Long targetId;
    private String eventCode;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
