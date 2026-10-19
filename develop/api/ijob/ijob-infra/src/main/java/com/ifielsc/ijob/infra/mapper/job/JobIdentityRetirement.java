// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobIdentityRetirement {
    private Long jobId;
    private Long expectedDefinitionId;
    private Long expectedRevision;
    private LocalDateTime retiredTime;
}
