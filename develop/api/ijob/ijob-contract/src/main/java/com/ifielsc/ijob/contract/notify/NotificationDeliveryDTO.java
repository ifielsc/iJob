// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Delivery state only; never exposes the encrypted configuration snapshot or payload. */
@Getter
@Setter
public class NotificationDeliveryDTO {
    private Long id;
    private Long notifyConfigId;
    private String configName;
    private String notifyType;
    private String sourceType;
    private String subject;
    private Long targetId;
    private Long instanceId;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String failureCode;
    private String failureSummary;
    private Integer httpStatus;
    private LocalDateTime availableAt;
    private LocalDateTime createTime;
    private LocalDateTime deliveredAt;
    private LocalDateTime updateTime;
}
