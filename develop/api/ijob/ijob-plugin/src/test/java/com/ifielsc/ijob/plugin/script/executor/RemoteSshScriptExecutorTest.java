package com.ifielsc.ijob.plugin.script.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.plugin.script.ScriptEngine;
import com.ifielsc.ijob.plugin.script.ScriptTaskConfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

class RemoteSshScriptExecutorTest {
    private final RemoteSshScriptExecutor executor = new RemoteSshScriptExecutor(null, null, null);

    @Test
    void shellCommandShouldQuoteWorkingDirectoryAndEnvironment() {
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(ScriptEngine.SHELL);
        config.setWorkingDir("/srv/team's jobs");
        LinkedHashMap<String, String> environment = new LinkedHashMap<>();
        environment.put("MODE", "safe value");
        environment.put("QUOTE", "a'b");
        config.setEnv(environment);

        assertEquals(
                "cd '/srv/team'\"'\"'s jobs' && env MODE='safe value' "
                        + "QUOTE='a'\"'\"'b' /bin/sh -s",
                executor.buildCommand(config));
    }

    @Test
    void pythonCommandShouldUseConfiguredRemoteInterpreter() {
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(ScriptEngine.PYTHON);
        config.setInterpreter("/usr/bin/python3");

        assertEquals("/usr/bin/python3 -", executor.buildCommand(config));
    }
}
