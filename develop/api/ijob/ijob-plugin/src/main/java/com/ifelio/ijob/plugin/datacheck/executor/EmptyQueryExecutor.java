package com.ifelio.ijob.plugin.datacheck.executor;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.datacheck.config.DataCheckItemConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmptyQueryExecutor {
    private final JdbcExecutor jdbcExecutor;

    public Boolean execute(
            Long resourceId, DataCheckItemConfig config, int timeoutSeconds, TaskContext context) {
        return !jdbcExecutor.exists(resourceId, config.getSql(), timeoutSeconds, context);
    }
}
