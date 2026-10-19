// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.command;

import com.ifielsc.ijob.contract.enums.RuntimeCommandStatus;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RuntimeCommand {
    private String requestId;
    private Long instanceId;
    private Long rootInstanceId;
    private Long triggerId;
    private RuntimeCommandType commandType;
    private RuntimeCommandStatus status;
    private String requestedBy;
    private String resultMessage;
    private LocalDateTime availableAt;
    private LocalDateTime expiresAt;
    private String processingOwner;
    private String processingToken;
    private Long processingFence;
    private LocalDateTime processedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
