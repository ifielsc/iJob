package com.ifielsc.ijob.plugin.script.executor;

import com.ifielsc.ijob.plugin.script.ProcessStreamCollector;
import com.ifielsc.ijob.plugin.script.ScriptEngine;
import com.ifielsc.ijob.plugin.script.ScriptTaskConfig;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class ShellScriptExecutor extends AbstractProcessScriptExecutor {
    public ShellScriptExecutor(
            @Qualifier("scriptExecutorPool") ExecutorService scriptExecutorPool,
            ProcessStreamCollector streamCollector) {
        super(scriptExecutorPool, streamCollector);
    }

    @Override
    public ScriptEngine support() {
        return ScriptEngine.SHELL;
    }

    @Override
    protected List<String> buildCommand(ScriptTaskConfig config, File scriptFile) {
        return List.of("/bin/sh", scriptFile.getAbsolutePath());
    }
}
