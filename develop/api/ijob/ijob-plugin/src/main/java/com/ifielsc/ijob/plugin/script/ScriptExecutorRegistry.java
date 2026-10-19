// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.script;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ScriptExecutorRegistry {
    private final Map<ScriptEngine, ScriptExecutor> executorMap;

    public ScriptExecutorRegistry(List<ScriptExecutor> executors) {
        executorMap =
                executors.stream()
                        .collect(Collectors.toMap(ScriptExecutor::support, Function.identity()));
    }

    public ScriptExecutor get(ScriptEngine engine) {
        ScriptExecutor executor = executorMap.get(engine);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported script engine: " + engine);
        }
        return executor;
    }
}
