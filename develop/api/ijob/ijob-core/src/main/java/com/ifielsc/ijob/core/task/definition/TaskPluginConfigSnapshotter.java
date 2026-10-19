// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.contract.enums.PluginType;

/** Produces a stable plugin configuration before a task definition is stored. */
public interface TaskPluginConfigSnapshotter {
    PluginType supportType();

    String snapshot(String pluginConfig);
}
