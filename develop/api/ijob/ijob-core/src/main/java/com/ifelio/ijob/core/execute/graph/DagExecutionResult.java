package com.ifelio.ijob.core.execute.graph;

import com.ifelio.ijob.contract.enums.InstanceStatus;

public record DagExecutionResult(
        InstanceStatus status,
        int successCount,
        int failedCount,
        int timedOutCount,
        int stoppedCount,
        int skippedCount) {}
