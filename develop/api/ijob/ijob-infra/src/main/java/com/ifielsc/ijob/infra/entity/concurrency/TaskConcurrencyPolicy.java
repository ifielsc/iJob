// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.concurrency;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TaskConcurrencyPolicy {
    private Long taskId;
    private Integer taskMaxConcurrency;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
