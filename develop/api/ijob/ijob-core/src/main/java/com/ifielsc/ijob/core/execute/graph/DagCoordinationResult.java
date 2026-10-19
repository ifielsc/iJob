// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.contract.enums.InstanceStatus;

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
