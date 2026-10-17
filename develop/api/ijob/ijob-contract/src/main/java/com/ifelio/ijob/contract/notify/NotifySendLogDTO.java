package com.ifelio.ijob.contract.notify;

import com.ifelio.ijob.contract.enums.NotifySendResult;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
public class NotifySendLogDTO {
    private Long id;
    private NotifySendSource sourceType;

    @ToString.Exclude private String correlationId;

    @ToString.Exclude private String operatorId;

    private TargetType targetType;
    private Long targetId;
    private Long instanceId;
    private Long notifyBindingId;
    private Long notifyConfigId;
    private NotifyType notifyType;
    private NotifySendResult result;
    private String failureCode;

    @ToString.Exclude private String failureSummary;

    private Long durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
