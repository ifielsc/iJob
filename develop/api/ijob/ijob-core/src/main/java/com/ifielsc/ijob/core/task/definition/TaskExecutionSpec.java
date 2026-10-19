package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.contract.enums.PluginType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString(exclude = "pluginConfig")
public final class TaskExecutionSpec {
    private final String name;
    private final PluginType pluginType;
    private final String pluginConfig;
    private final int retryTimes;
    private final int retryInterval;
    private final Integer timeout;
    private final boolean enabled;
    private final String description;
}
