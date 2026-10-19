package com.ifielsc.ijob.core.execute.task;

import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;

public interface TaskRunner {
    PluginType supportType();

    TaskResult run(TaskExecutionSpec spec, TaskContext context);

    default void stop(TaskExecutionSpec spec, TaskContext context) {}
}
