// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.core.execute.graph.JobExecutionNode;

import java.util.Set;

public record NodeExecuteResult(
        Long nodeId,
        NodeExecuteStatus status,
        FailStrategy failStrategy,
        Throwable failure,
        boolean inactivePath,
        Set<Long> selectedEdgeIds) {
    public static NodeExecuteResult of(JobExecutionNode node, NodeExecuteStatus status) {
        return new NodeExecuteResult(node.id(), status, node.failStrategy(), null, false, Set.of());
    }

    public static NodeExecuteResult failed(JobExecutionNode node, Throwable failure) {
        return new NodeExecuteResult(
                node.id(), NodeExecuteStatus.FAILED, node.failStrategy(), failure, false, Set.of());
    }

    public static NodeExecuteResult branchNotSelected(JobExecutionNode node) {
        return new NodeExecuteResult(
                node.id(), NodeExecuteStatus.SKIPPED, node.failStrategy(), null, true, Set.of());
    }

    public static NodeExecuteResult gateway(JobExecutionNode node, Set<Long> selectedEdgeIds) {
        return new NodeExecuteResult(
                node.id(),
                NodeExecuteStatus.SUCCESS,
                node.failStrategy(),
                null,
                false,
                Set.copyOf(selectedEdgeIds));
    }

    public boolean failedOrTimedOut() {
        return status == NodeExecuteStatus.FAILED || status == NodeExecuteStatus.TIMED_OUT;
    }
}
