package com.ifelio.ijob.core.execute.graph;

import com.ifelio.ijob.contract.enums.InstanceStatus;

import java.util.Map;

public record DagCoordinationResult(
        Map<Long, InstanceStatus> nodeResults, boolean abortedByFailure, boolean suspended) {
    public DagCoordinationResult(Map<Long, InstanceStatus> nodeResults, boolean abortedByFailure) {
        this(nodeResults, abortedByFailure, false);
    }

    public DagCoordinationResult {
        nodeResults = Map.copyOf(nodeResults);
    }
}
