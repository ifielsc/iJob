package com.ifielsc.ijob.plugin.script;

import lombok.Data;

import java.util.Map;

@Data
public class ScriptTaskConfig {
    private ScriptExecutionMode executionMode;
    private Long sshResourceId;
    private ScriptEngine engine;
    private String interpreter;
    private String script;
    private String workingDir;
    private Map<String, String> env;

    /** Legacy setting applied to both the script file and process output. */
    private String charset;

    private String scriptCharset;
    private String outputCharset;
}
