package com.ifelio.ijob.contract.concurrency;

import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PluginConcurrencyPolicyDTO {
    private PluginType pluginType;
    private Integer maxConcurrency;
    private Long version;
    private Integer activeInstanceCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
