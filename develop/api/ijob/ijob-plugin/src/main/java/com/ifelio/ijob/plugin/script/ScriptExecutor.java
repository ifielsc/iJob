package com.ifelio.ijob.plugin.script;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;

public interface ScriptExecutor {
    ScriptEngine support();

    ScriptResult execute(ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context)
            throws Exception;
}
