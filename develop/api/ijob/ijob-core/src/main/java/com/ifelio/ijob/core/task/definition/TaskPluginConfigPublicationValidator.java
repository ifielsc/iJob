package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.contract.enums.PluginType;

import java.util.Set;

public interface TaskPluginConfigPublicationValidator {
    Set<PluginType> supportTypes();

    void validate(PluginType type, String pluginConfig, TaskPluginConfigPublicationContext context);

    default Set<Long> referencedResourceIds(PluginType type, String pluginConfig) {
        return Set.of();
    }
}
