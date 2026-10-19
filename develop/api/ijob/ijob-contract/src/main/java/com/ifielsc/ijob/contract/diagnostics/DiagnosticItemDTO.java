// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.diagnostics;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Explicit public projection: excludes owners, tokens, raw errors and business content. */
@Getter
@Setter
public class DiagnosticItemDTO {
    private String id;
    private String triggerId;
    private String rootInstanceId;
    private String instanceId;
    private String targetType;
    private String targetId;
    private String name;
    private String status;
    private String commandType;
    private LocalDateTime observedDeadline;
    private LocalDateTime createTime;
}
