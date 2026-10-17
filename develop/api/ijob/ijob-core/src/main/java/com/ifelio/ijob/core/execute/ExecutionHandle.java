package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.common.exception.StopException;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.task.TaskRunner;
import com.ifelio.ijob.core.task.definition.TaskExecutionSpec;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class ExecutionHandle {
    private final Long instanceId;

    private volatile boolean stopRequested;
    private volatile boolean timeoutRequested;
    private volatile boolean pauseRequested;
    private volatile com.ifelio.ijob.core.execute.task.CancellationReason cancellationReason;

    private final Object pauseLock = new Object();

    private volatile TaskRunner runner;
    private volatile TaskExecutionSpec executionSpec;
    private volatile TaskContext context;

    public ExecutionHandle(Long instanceId) {
        this.instanceId = instanceId;
    }

    public void bind(TaskRunner runner, TaskExecutionSpec spec, TaskContext context) {
        this.runner = runner;
        this.executionSpec = spec;
        this.context = context;
        if (cancellationReason != null) context.cancel(cancellationReason);
    }

    public void unbind() {
        this.runner = null;
        this.executionSpec = null;
        this.context = null;
    }

    public void requestStop() {
        requestCancellation(com.ifelio.ijob.core.execute.task.CancellationReason.STOP);
    }

    public void requestTimeout() {
        this.timeoutRequested = true;
        requestCancellation(com.ifelio.ijob.core.execute.task.CancellationReason.TIMEOUT);
    }

    public void requestFence() {
        requestCancellation(com.ifelio.ijob.core.execute.task.CancellationReason.FENCED);
    }

    private void requestCancellation(com.ifelio.ijob.core.execute.task.CancellationReason reason) {
        synchronized (pauseLock) {
            if (cancellationReason == null
                    || reason == com.ifelio.ijob.core.execute.task.CancellationReason.FENCED)
                cancellationReason = reason;
            stopRequested = true;
        }
        TaskContext current = context;
        if (current != null) current.cancel(cancellationReason);
        stopRunner();
    }

    private void stopRunner() {
        resume();

        TaskRunner currentRunner = runner;
        TaskExecutionSpec currentSpec = executionSpec;
        TaskContext currentContext = context;
        if (currentRunner != null && currentSpec != null && currentContext != null) {
            try {
                currentRunner.stop(currentSpec, currentContext);
            } catch (Exception exception) {
                log.warn(
                        "plugin stop hook failed, instanceId={}",
                        instanceId,
                        com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            }
        }
    }

    public void requestPause() {
        this.pauseRequested = true;
    }

    public void resume() {
        synchronized (pauseLock) {
            pauseRequested = false;
            pauseLock.notifyAll();
        }
    }

    public void checkStop() {
        if (stopRequested) {
            throw new StopException();
        }
    }

    public void checkPause() {
        while (pauseRequested) {
            checkStop();
            synchronized (pauseLock) {
                try {
                    pauseLock.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StopException();
                }
            }
        }
        checkStop();
    }
}
