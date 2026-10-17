package com.ifelio.ijob.framework.config;

public record ExecutionPoolSnapshot(
        String name,
        int coreSize,
        int maxSize,
        int poolSize,
        int activeCount,
        int queuedTaskCount,
        int queueRemainingCapacity,
        long completedTaskCount,
        long rejectedTaskCount,
        boolean shutdown,
        boolean terminated) {}
