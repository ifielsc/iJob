package com.ifielsc.ijob.infra.entity.event;

import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

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
