package com.ifielsc.ijob.contract.concurrency;

import lombok.Data;

@Data
public class PluginConcurrencyPolicyUpdateRequest {
    private Long expectedVersion;
    private Integer maxConcurrency;
}
