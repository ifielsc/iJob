package com.ifelio.ijob.infra.entity.runtime;

import com.ifelio.ijob.contract.enums.EventWaitStatus;
import com.ifelio.ijob.contract.enums.EventWaitType;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EventWait {
    @AutoId(IdNamespace.EVENT_WAIT)
    private Long id;

    private Long instanceId;
    private Long rootInstanceId;
    private Integer attemptNo;
    private EventWaitType waitType;
    private EventWaitStatus status;
    private String eventCode;
    private String businessKey;
    private String correlationKey;
    private LocalDateTime acceptAfter;
    private LocalDateTime timeoutAt;
    private Long matchedEventId;
    private String allowedOperatorIds;
    private String decisionBy;
    private String decisionComment;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
