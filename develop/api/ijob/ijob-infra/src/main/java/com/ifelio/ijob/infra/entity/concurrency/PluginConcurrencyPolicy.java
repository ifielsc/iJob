package com.ifelio.ijob.infra.entity.concurrency;

import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PluginConcurrencyPolicy {
    private PluginType pluginType;
    private Integer maxConcurrency;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
