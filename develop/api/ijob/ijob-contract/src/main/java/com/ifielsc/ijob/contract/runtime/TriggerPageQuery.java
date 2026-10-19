// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.runtime;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.contract.enums.TriggerType;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class TriggerPageQuery extends BasePageQuery {
    private TargetType targetType;
    private Long targetId;
    private TriggerType triggerType;
    private TriggerStatus status;
    private Long scheduleId;
    private LocalDateTime from;
    private LocalDateTime to;
    private String name;
}
