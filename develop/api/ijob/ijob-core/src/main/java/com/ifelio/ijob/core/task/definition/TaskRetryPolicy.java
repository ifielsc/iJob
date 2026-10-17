package com.ifelio.ijob.core.task.definition;

/**
 * Shared publication/runtime bounds, including immutable definitions published before validation.
 */
public final class TaskRetryPolicy {
    public static final int MAX_RETRIES = 100;
    public static final int MAX_INTERVAL_SECONDS = 3600;
    public static final long MAX_TOTAL_DELAY_SECONDS = 86400;

    private TaskRetryPolicy() {}

    public static void validate(int retries, int intervalSeconds) {
        if (retries < 0
                || retries > MAX_RETRIES
                || intervalSeconds < 0
                || intervalSeconds > MAX_INTERVAL_SECONDS
                || (long) retries * intervalSeconds > MAX_TOTAL_DELAY_SECONDS) {
            throw new IllegalArgumentException("Retry settings exceed execution limits");
        }
    }
}
