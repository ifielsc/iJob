// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.enums.NotifyPolicy;
import com.ifielsc.ijob.contract.enums.TargetType;

import lombok.Data;

@Data
public class NotifyBindingCreateRequest {
    private TargetType targetType;
    private Long targetId;
    private Long notifyConfigId;
    private NotifyPolicy notifyPolicy;
}
