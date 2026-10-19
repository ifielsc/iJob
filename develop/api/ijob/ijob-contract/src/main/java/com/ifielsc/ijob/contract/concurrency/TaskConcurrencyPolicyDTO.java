// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskConcurrencyPolicyDTO {
    private Long taskId;
    private String taskName;
    private com.ifielsc.ijob.contract.enums.PluginType pluginType;
    private Integer activeInstanceCount;
    private Integer taskMaxConcurrency;
    private Long version;
    private List<ConcurrencyResourceDTO> resources;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
