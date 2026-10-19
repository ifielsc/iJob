// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

@Data
public class PluginConcurrencyPolicyUpdateRequest {
    private Long expectedVersion;
    private Integer maxConcurrency;
}
