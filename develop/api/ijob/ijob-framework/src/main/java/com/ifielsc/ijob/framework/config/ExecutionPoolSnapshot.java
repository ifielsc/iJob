// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.config;

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
