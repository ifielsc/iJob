// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.model;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DataCheckContext {
    private final Long resourceId;
    private final List<DataCheckItemConfig> checks;

    /** Task instance ID. */
    private final Long instanceId;

    private final TaskContext taskContext;

    public void putVariable(String key, Object value) {
        if (taskContext == null) {
            return;
        }
        if (value == null) {
            taskContext.removeVariable(key);
        } else {
            taskContext.putVariable(key, value);
        }
    }

    public Object getVariable(String key) {
        return taskContext == null ? null : taskContext.getVariable(key);
    }

    public void checkExecution() {
        if (taskContext != null) {
            taskContext.checkStop();
            taskContext.checkPause();
            taskContext.checkInterrupted();
        }
    }
}
