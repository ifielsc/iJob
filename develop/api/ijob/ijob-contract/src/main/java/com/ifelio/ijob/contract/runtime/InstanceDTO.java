package com.ifelio.ijob.contract.runtime;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstanceDTO {
    private Long id;
    private Long nodeId;
    private Long parentInstanceId;
    private Long rootInstanceId;
    private TargetType targetType;
    private Long targetId;
    private String targetName;
    private TriggerType triggerType;
    private Long triggerId;
    private InstanceStatus status;
    private Integer progress;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime timeoutAt;
    private String waitReason;
    private LocalDateTime permitWaitDeadline;
    private LocalDateTime endTime;
    private String message;
    private String displayMessage;
    private String errorCode;
    private String messageKey;
    private String messageArgs;
    private Long jobDefinitionId;
    private Long definitionNodeId;
    private Long taskDefinitionId;
    private String inputVariables;
    private String outputVariables;
    private boolean variablesTruncated;
    private boolean variablesRedacted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
