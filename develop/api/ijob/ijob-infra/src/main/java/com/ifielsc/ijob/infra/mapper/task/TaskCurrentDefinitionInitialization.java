// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.task;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskCurrentDefinitionInitialization {
    private Long taskId;
    private Long definitionId;
    private LocalDateTime updateTime;
}
