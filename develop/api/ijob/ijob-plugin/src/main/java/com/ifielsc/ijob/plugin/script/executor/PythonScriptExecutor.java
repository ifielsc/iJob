// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script.executor;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.plugin.script.ProcessStreamCollector;
import com.ifielsc.ijob.plugin.script.ScriptEngine;
import com.ifielsc.ijob.plugin.script.ScriptTaskConfig;

import io.micrometer.common.util.StringUtils;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class PythonScriptExecutor extends AbstractProcessScriptExecutor {
    public PythonScriptExecutor(
            @Qualifier("scriptExecutorPool") ExecutorService scriptExecutorPool,
            ProcessStreamCollector streamCollector) {
        super(scriptExecutorPool, streamCollector);
    }

    @Override
    public ScriptEngine support() {
        return ScriptEngine.PYTHON;
    }

    @Override
    protected List<String> buildCommand(ScriptTaskConfig config, File scriptFile) {
        if (StringUtils.isBlank(config.getInterpreter())) {
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Python interpreter is required");
        }

        return List.of(config.getInterpreter(), scriptFile.getAbsolutePath());
    }
}
