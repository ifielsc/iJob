// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.event;

import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Data;

@Data
public class EventBindingCreateRequest {
    private TargetType targetType;
    private Long targetId;
    private String eventCode;
    private Boolean enabled = true;
}
