package com.ifelio.ijob.plugin.push.publisher;

public final class PublishMetrics {
    private long attemptedCount;
    private long successCount;
    private long failedCount;

    public synchronized void begin() {
        attemptedCount++;
    }

    public synchronized void complete(Throwable failure) {
        if (failure == null) {
            successCount++;
        } else {
            failedCount++;
        }
    }

    public synchronized long getAttemptedCount() {
        return attemptedCount;
    }

    public synchronized long getSuccessCount() {
        return successCount;
    }

    public synchronized long getFailedCount() {
        return failedCount;
    }
}
