package com.ifelio.ijob.infra.entity.notify;

import com.ifelio.ijob.contract.enums.NotifyPolicy;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotifyBinding {
    @AutoId(IdNamespace.NOTIFY_BINDING)
    private Long id;

    private TargetType targetType;
    private Long targetId;
    private Long notifyConfigId;
    private NotifyPolicy notifyPolicy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
