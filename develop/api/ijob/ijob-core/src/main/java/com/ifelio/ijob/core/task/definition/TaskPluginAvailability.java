package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.contract.enums.PluginType;

public interface TaskPluginAvailability {
    boolean supports(PluginType type);

    boolean isEnabled(PluginType type);
}
