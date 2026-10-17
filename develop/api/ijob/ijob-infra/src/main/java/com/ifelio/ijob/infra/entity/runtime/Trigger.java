package com.ifelio.ijob.infra.entity.runtime;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerStatus;
import com.ifelio.ijob.contract.enums.TriggerType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Trigger {
    @AutoId(IdNamespace.TRIGGER)
    private Long id;

    private Long scheduleId;
    private TargetType targetType;
    private Long targetId;
    private TriggerType triggerType;
    private String idempotencyKey;
    private String eventCode;
    private TriggerStatus status;
    private LocalDateTime triggerTime;
    private String businessDate;
    private String businessTime;
    private String businessZoneId;
    private String message;
    private String errorCode;
    private String messageKey;
    private String messageArgs;
    private Long definitionId;
    private Long taskDefinitionId;
    private String variables;
    private Long eventId;
    private Long eventBindingId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
