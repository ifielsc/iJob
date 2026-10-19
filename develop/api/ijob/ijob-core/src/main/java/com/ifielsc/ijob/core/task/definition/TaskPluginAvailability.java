// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.task.definition;

import com.ifielsc.ijob.contract.enums.PluginType;

public interface TaskPluginAvailability {
    boolean supports(PluginType type);

    boolean isEnabled(PluginType type);
}
