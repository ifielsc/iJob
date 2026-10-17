package com.ifelio.ijob.contract.concurrency;

import com.ifelio.ijob.contract.enums.PluginType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PluginActivityDTO {
    private PluginType type;
    private Integer activeInstanceCount;
}
