// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.runtime;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EtlCheckpoint {
    private Long taskId;
    private String checkpointKey;
    private String sourceFingerprint;
    private String watermarkType;
    private String watermarkValue;
    private String tieBreakerValue;
    private Long lastInstanceId;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
