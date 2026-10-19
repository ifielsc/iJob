package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.contract.enums.InstanceStatus;

public record DagExecutionResult(
        InstanceStatus status,
        int successCount,
        int failedCount,
        int timedOutCount,
        int stoppedCount,
        int skippedCount) {}
