package com.ifelio.ijob.infra.coordination.notification;

import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationOutboxItem {
    @AutoId(IdNamespace.NOTIFICATION_OUTBOX)
    private Long id;

    private String deduplicationKey;
    private Long notifyConfigId;
    private NotifyType notifyType;
    private String configSnapshot;
    private Boolean configEnabled;
    private String title;
    private String subject;
    private String content;
    private int contentVersion;
    private Integer httpStatus;
    private NotifySendSource sourceType;
    private String correlationId;
    private String operatorId;
    private TargetType targetType;
    private Long targetId;
    private Long instanceId;
    private Long notifyBindingId;
    private NotificationOutboxStatus status;
    private LocalDateTime availableAt;
    private String leaseOwner;
    private String claimToken;
    private LocalDateTime leaseDeadline;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lastFailureCode;
    private String lastFailureSummary;
    private LocalDateTime deliveredAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
