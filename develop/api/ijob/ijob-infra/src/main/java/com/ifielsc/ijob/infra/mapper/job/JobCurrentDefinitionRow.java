// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobCurrentDefinitionRow {
    private Long id;
    private Long currentDefinitionId;
    private Long currentRevision;
    private Boolean retired;
    private LocalDateTime retiredTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
