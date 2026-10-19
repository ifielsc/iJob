// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.runtime;

import com.ifielsc.ijob.contract.enums.LogLevel;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstanceLogDTO {
    private Long id;
    private Long instanceId;
    private LocalDateTime logTime;
    private LogLevel level;
    private String content;
}
