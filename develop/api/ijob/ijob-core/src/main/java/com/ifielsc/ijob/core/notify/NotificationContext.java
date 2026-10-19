package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationContext {
    private Long instanceId;
    private TargetType targetType;
    private Long targetId;
    private String targetName;
    private InstanceStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private Integer retryCount;
    private String message;
    private String messageKey;
    private Object[] messageArgs;
}
