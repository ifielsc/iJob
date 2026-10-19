// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.event;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.EventWaitStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventWaitPageQuery extends BasePageQuery {
    private String taskName;

    public String getTaskNamePattern() {
        if (taskName == null || taskName.isEmpty()) return null;
        return "%"
                + taskName.replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                        .replace("[", "![")
                + "%";
    }

    private Long instanceId;
    private Long rootInstanceId;
    private EventWaitStatus status = EventWaitStatus.WAITING;
}
