package com.ifielsc.ijob.plugin.script;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.task.definition.TaskExecutionSpec;

public interface ScriptExecutor {
    ScriptEngine support();

    ScriptResult execute(ScriptTaskConfig config, TaskExecutionSpec spec, TaskContext context)
            throws Exception;
}
