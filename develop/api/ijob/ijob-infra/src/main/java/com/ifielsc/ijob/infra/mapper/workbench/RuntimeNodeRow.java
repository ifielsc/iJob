// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.workbench;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RuntimeNodeRow {
    private String runId;
    private String instanceName;
    private String roles;
    private String workerGroups;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime heartbeatAt;
    private LocalDateTime stoppedAt;
    private String telemetry;
}
