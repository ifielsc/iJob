package com.ifelio.ijob.contract.runtime;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerStatus;
import com.ifelio.ijob.contract.enums.TriggerType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TriggerDTO {
    private Long id;
    private Long scheduleId;
    private TargetType targetType;
    private Long targetId;
    private TriggerType triggerType;
    private String eventCode;
    private TriggerStatus status;
    private LocalDateTime triggerTime;
    private String businessDate;
    private String businessTime;
    private String businessZoneId;
    private String message;
    private String displayMessage;
    private String errorCode;
    private String messageKey;
    private String messageArgs;
    private Long jobDefinitionId;
    private Long taskDefinitionId;
    private Long rootInstanceId;
    private String targetName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
