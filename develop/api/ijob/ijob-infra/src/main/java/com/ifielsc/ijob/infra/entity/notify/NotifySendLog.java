// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.notify;

import com.ifielsc.ijob.contract.enums.NotifySendResult;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotifySendLog {
    @AutoId(IdNamespace.NOTIFY_SEND_LOG)
    private Long id;

    private NotifySendSource sourceType;
    private String correlationId;
    private String operatorId;
    private TargetType targetType;
    private Long targetId;
    private Long instanceId;
    private Long notifyBindingId;
    private Long notifyConfigId;
    private NotifyType notifyType;
    private NotifySendResult result;
    private String failureCode;
    private String failureSummary;
    private Long durationMs;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
