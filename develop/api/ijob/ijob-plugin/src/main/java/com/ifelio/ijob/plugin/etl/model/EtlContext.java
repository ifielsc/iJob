package com.ifelio.ijob.plugin.etl.model;

import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.incremental.EtlIncrementalSession;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class EtlContext {
    private final TaskContext taskContext;
    private final EtlTaskConfig config;
    private final Map<String, Object> variables;
    private com.ifelio.ijob.plugin.etl.loader.TargetMappingPlan targetMappingPlan;
    private java.nio.file.Path stagedSource;
    private Runnable writeGuard = () -> {};
    private boolean deferPause;
    private long extractCount;
    private long transformCount;
    private long validateErrorCount;
    private long loadCount;
    private long insertCount;
    private long updateCount;
    private long skipCount;
    private long protectedCount;
    private EtlIncrementalSession incrementalSession = EtlIncrementalSession.disabled();

    public EtlContext(TaskContext taskContext, EtlTaskConfig config) {
        this.taskContext = taskContext;
        this.config = config;
        this.variables =
                Collections.unmodifiableMap(new LinkedHashMap<>(taskContext.getVariables()));
    }

    public Long getInstanceId() {
        return taskContext.getInstanceId();
    }

    public int getBatchSize() {
        return config.messageSource()
                ? config.getMessage().getBatchSize()
                : config.getExecution().getReadBatchSize();
    }

    public void checkControl() {
        taskContext.checkStop();
        if (!deferPause) taskContext.checkPause();
        taskContext.checkInterrupted();
    }

    public void progress(int value) {
        taskContext.progress(Math.max(0, Math.min(99, value)));
    }
}
