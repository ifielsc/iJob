package com.ifielsc.ijob.plugin.datacheck.executor;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExistsQueryExecutor {
    private final JdbcExecutor jdbcExecutor;

    public Boolean execute(
            Long resourceId, DataCheckItemConfig config, int timeoutSeconds, TaskContext context) {
        return jdbcExecutor.exists(resourceId, config.getSql(), timeoutSeconds, context);
    }
}
