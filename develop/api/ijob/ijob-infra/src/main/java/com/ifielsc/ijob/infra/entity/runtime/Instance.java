// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.runtime;

import com.ifielsc.ijob.contract.enums.InstanceStatus;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Instance {
    @AutoId(IdNamespace.INSTANCE)
    private Long id;

    private Long nodeId;
    private Long parentInstanceId;
    private Long rootInstanceId;
    private Long executionFence;
    private TargetType targetType;
    private Long targetId;
    // Read projection from the immutable definition; not a persisted instance column.
    private transient String targetName;
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
    private String errorCode;
    private String messageKey;
    private String messageArgs;
    private Long definitionId;
    private Long definitionNodeId;
    private Long taskDefinitionId;
    private String inputVariables;
    private String outputVariables;
    // Query-only projection metadata; not a persisted instance column.
    private transient boolean variablesTruncated;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isRoot() {
        return parentInstanceId == null;
    }
}
