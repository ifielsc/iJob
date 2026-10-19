package com.ifielsc.ijob.contract.event;

import com.ifielsc.ijob.contract.enums.EventWaitStatus;
import com.ifielsc.ijob.contract.enums.EventWaitType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventWaitDTO {
    private Long id;
    private String taskName;
    private String rootTargetName;
    private Long taskId;
    private Long taskDefinitionId;
    private Integer attemptNo;
    private Long instanceId;
    private Long rootInstanceId;
    private EventWaitType waitType;
    private EventWaitStatus status;
    private String eventCode;
    private String businessKey;
    private String correlationKey;
    private LocalDateTime acceptAfter;
    private LocalDateTime timeoutAt;
    private Long matchedEventId;
    private String decisionBy;
    private String decisionComment;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
