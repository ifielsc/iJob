// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.event;

import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventBindingDTO {
    private Long id;
    private TargetType targetType;
    private Long targetId;
    private String eventCode;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
