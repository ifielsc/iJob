package com.ifelio.ijob.core.execute.task;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;

public interface TaskRunner {
    PluginType supportType();

    TaskResult run(TaskExecutionSpec spec, TaskContext context);

    default void stop(TaskExecutionSpec spec, TaskContext context) {}
}
