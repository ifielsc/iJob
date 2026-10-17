package com.ifelio.ijob.core.task.definition;

import com.ifelio.ijob.contract.enums.PluginType;

/** Produces a stable plugin configuration before a task definition is stored. */
public interface TaskPluginConfigSnapshotter {
    PluginType supportType();

    String snapshot(String pluginConfig);
}
