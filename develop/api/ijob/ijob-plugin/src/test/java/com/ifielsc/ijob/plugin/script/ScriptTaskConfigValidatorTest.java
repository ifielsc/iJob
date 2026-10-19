// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

class ScriptTaskConfigValidatorTest {
    private final ScriptTaskConfigValidator validator = new ScriptTaskConfigValidator();

    @Test
    void publicationMustNotCheckTheApiHostsFilesystem(@TempDir Path directory) {
        ScriptTaskConfig config = config();
        config.setWorkingDir(directory.resolve("worker-only-directory").toString());
        assertDoesNotThrow(() -> validator.validateForPublication(config));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
        config.setWorkingDir("/worker/linux-only/path");
        assertDoesNotThrow(() -> validator.validateForPublication(config));
        config.setWorkingDir("invalid\0path");
        assertThrows(
                IllegalArgumentException.class, () -> validator.validateForPublication(config));
    }

    @Test
    void remotePythonWithMissingInterpreterReturnsValidationError() {
        ScriptTaskConfig config = config();
        config.setEngine(ScriptEngine.PYTHON);
        config.setExecutionMode(ScriptExecutionMode.SSH);
        config.setSshResourceId(1L);
        assertEquals(
                "Python interpreter is required",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validateForPublication(config))
                        .getMessage());
    }

    @Test
    void validConfigurationShouldPass(@TempDir Path workingDirectory) {
        ScriptTaskConfig config = config();
        config.setWorkingDir(workingDirectory.toString());
        config.setCharset("UTF-8");
        config.setScriptCharset("UTF-16LE");
        config.setOutputCharset("GBK");
        config.setEnv(Map.of("MODE", "test"));

        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void engineAndScriptShouldBeRequired() {
        ScriptTaskConfig missingEngine = config();
        missingEngine.setEngine(null);
        ScriptTaskConfig missingScript = config();
        missingScript.setScript("  ");

        assertEquals(
                "Script engine is required",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingEngine))
                        .getMessage());
        assertEquals(
                "Script content is required",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingScript))
                        .getMessage());
    }

    @Test
    void invalidCharsetWorkingDirectoryAndEnvironmentShouldFail(@TempDir Path workingDirectory) {
        ScriptTaskConfig invalidCharset = config();
        invalidCharset.setCharset("not/a/charset");
        ScriptTaskConfig invalidDirectory = config();
        invalidDirectory.setWorkingDir(workingDirectory.resolve("missing").toString());
        ScriptTaskConfig invalidEnvironment = config();
        invalidEnvironment.setEnv(Map.of("", "value"));
        ScriptTaskConfig invalidOutputCharset = config();
        invalidOutputCharset.setOutputCharset("not/a/charset");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidCharset));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidDirectory));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalidEnvironment));
        assertThrows(
                IllegalArgumentException.class, () -> validator.validate(invalidOutputCharset));
    }

    @Test
    void pythonInterpreterShouldBeRequired() {
        ScriptTaskConfig config = config();
        config.setEngine(ScriptEngine.PYTHON);

        assertEquals(
                "Python interpreter is required",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(config))
                        .getMessage());
    }

    @Test
    void sshModeShouldAllowOnlyShellAndPythonWithResource() {
        ScriptTaskConfig shell = config();
        shell.setExecutionMode(ScriptExecutionMode.SSH);
        shell.setSshResourceId(7L);
        shell.setWorkingDir("/srv/jobs");
        assertDoesNotThrow(() -> validator.validate(shell));

        ScriptTaskConfig powershell = config();
        powershell.setExecutionMode(ScriptExecutionMode.SSH);
        powershell.setSshResourceId(7L);
        powershell.setEngine(ScriptEngine.POWERSHELL);
        assertEquals(
                "SSH mode supports only SHELL and PYTHON",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(powershell))
                        .getMessage());

        ScriptTaskConfig missingResource = config();
        missingResource.setExecutionMode(ScriptExecutionMode.SSH);
        assertEquals(
                "SSH resource id must be greater than zero",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> validator.validate(missingResource))
                        .getMessage());
    }

    @Test
    void localModeShouldRemainTheLegacyDefault() {
        ScriptTaskConfig config = config();
        assertDoesNotThrow(() -> validator.validate(config));
        config.setSshResourceId(7L);
        assertEquals(
                "Local script cannot contain sshResourceId",
                assertThrows(IllegalArgumentException.class, () -> validator.validate(config))
                        .getMessage());
    }

    private ScriptTaskConfig config() {
        ScriptTaskConfig config = new ScriptTaskConfig();
        config.setEngine(ScriptEngine.SHELL);
        config.setScript("echo accepted");
        return config;
    }
}
