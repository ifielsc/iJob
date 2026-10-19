package com.ifielsc.ijob.plugin.script;

import io.micrometer.common.util.StringUtils;

import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ScriptTaskConfigValidator {
    private static final Pattern REMOTE_INTERPRETER = Pattern.compile("[A-Za-z0-9_./+~-]{1,512}");
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");

    public void validate(ScriptTaskConfig config) {
        validate(config, true);
    }

    public void validateForPublication(ScriptTaskConfig config) {
        validate(config, false);
    }

    private void validate(ScriptTaskConfig config, boolean execution) {
        if (config == null) {
            throw invalid("Script configuration is required");
        }
        if (config.getEngine() == null) {
            throw invalid("Script engine is required");
        }
        if (StringUtils.isBlank(config.getScript())) {
            throw invalid("Script content is required");
        }
        if (config.getEngine() == ScriptEngine.PYTHON
                && StringUtils.isBlank(config.getInterpreter())) {
            throw invalid("Python interpreter is required");
        }
        if (config.getWorkingDir() != null && config.getWorkingDir().indexOf('\0') >= 0) {
            throw invalid("Script working directory is invalid");
        }
        validateCharset(config.getCharset());
        validateCharset(config.getScriptCharset());
        validateCharset(config.getOutputCharset());
        ScriptExecutionMode mode =
                config.getExecutionMode() == null
                        ? ScriptExecutionMode.LOCAL
                        : config.getExecutionMode();
        if (mode == ScriptExecutionMode.LOCAL) {
            if (config.getSshResourceId() != null) {
                throw invalid("Local script cannot contain sshResourceId");
            }
            if (execution) validateWorkingDirectory(config.getWorkingDir());
        } else {
            validateRemote(config);
        }
        validateEnvironment(config.getEnv(), mode == ScriptExecutionMode.SSH);
    }

    private void validateRemote(ScriptTaskConfig config) {
        if (config.getSshResourceId() == null || config.getSshResourceId() <= 0) {
            throw invalid("SSH resource id must be greater than zero");
        }
        if (config.getEngine() != ScriptEngine.SHELL && config.getEngine() != ScriptEngine.PYTHON) {
            throw invalid("SSH mode supports only SHELL and PYTHON");
        }
        if (StringUtils.isNotBlank(config.getWorkingDir())) {
            if (!config.getWorkingDir().startsWith("/")
                    || config.getWorkingDir().length() > 4096
                    || config.getWorkingDir().indexOf('\0') >= 0) {
                throw invalid("SSH working directory must be an absolute path");
            }
        }
        if (config.getEngine() == ScriptEngine.PYTHON
                && !REMOTE_INTERPRETER.matcher(config.getInterpreter()).matches()) {
            throw invalid("SSH Python interpreter is invalid");
        }
    }

    private void validateCharset(String charset) {
        if (StringUtils.isBlank(charset)) {
            return;
        }
        try {
            Charset.forName(charset);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException exception) {
            throw invalid("Unsupported script charset: " + charset);
        }
    }

    private void validateWorkingDirectory(String workingDirectory) {
        if (StringUtils.isBlank(workingDirectory)) {
            return;
        }
        try {
            if (!Files.isDirectory(Path.of(workingDirectory))) {
                throw invalid("Script working directory does not exist: " + workingDirectory);
            }
        } catch (InvalidPathException exception) {
            throw invalid("Script working directory is invalid");
        }
    }

    private void validateEnvironment(Map<String, String> environment, boolean remote) {
        if (environment == null) {
            return;
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (StringUtils.isBlank(entry.getKey())
                    || entry.getValue() == null
                    || remote
                            && (!ENVIRONMENT_NAME.matcher(entry.getKey()).matches()
                                    || entry.getValue().indexOf('\0') >= 0)) {
                throw invalid("Script environment contains an invalid entry");
            }
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
