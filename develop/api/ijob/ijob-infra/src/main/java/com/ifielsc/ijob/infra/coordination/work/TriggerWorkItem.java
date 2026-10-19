// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.work;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TriggerWorkItem {
    private Long triggerId;
    private Long rootInstanceId;
    private TriggerWorkStatus status;
    private LocalDateTime availableAt;
    private LocalDateTime expiresAt;
    private String leaseOwner;
    private String claimToken;
    private LocalDateTime leaseDeadline;
    private long fenceToken;
    private int attemptCount;
    private int maxAttempts;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
