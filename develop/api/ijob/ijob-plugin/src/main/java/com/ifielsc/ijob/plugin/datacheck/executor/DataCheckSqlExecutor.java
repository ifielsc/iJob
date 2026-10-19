package com.ifielsc.ijob.plugin.datacheck.executor;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataCheckSqlExecutor {
    private final NormalQueryExecutor normalExecutor;
    private final CountQueryExecutor countExecutor;
    private final ExistsQueryExecutor existsExecutor;
    private final EmptyQueryExecutor emptyExecutor;

    public Object execute(
            Long resourceId, DataCheckItemConfig config, int timeoutSeconds, TaskContext context) {
        return switch (config.getType()) {
            case COUNT -> countExecutor.execute(resourceId, config, timeoutSeconds, context);
            case EXISTS -> existsExecutor.execute(resourceId, config, timeoutSeconds, context);
            case EMPTY -> emptyExecutor.execute(resourceId, config, timeoutSeconds, context);
            case NORMAL -> normalExecutor.execute(resourceId, config, timeoutSeconds, context);
        };
    }
}
