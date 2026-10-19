// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.contract.enums.InstanceStatus;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DependencyEvaluator {
    public boolean shouldExecute(
            JobExecutionNode node, JobExecutionPlan plan, Map<Long, InstanceStatus> nodeResults) {
        return shouldExecute(node, plan.parentMap().get(node.id()), nodeResults);
    }

    public boolean shouldExecute(
            JobExecutionNode node,
            List<Long> activeParents,
            Map<Long, InstanceStatus> nodeResults) {
        List<Long> parents = List.copyOf(activeParents);
        if (parents.isEmpty()) {
            return true;
        }
        if (parents.stream().anyMatch(parentId -> !isTerminal(nodeResults.get(parentId)))) {
            return false;
        }
        return switch (node.executeCondition()) {
            case ALL_SUCCESS ->
                    parents.stream().allMatch(parentId -> isSuccessful(nodeResults.get(parentId)));
            case ANY_SUCCESS ->
                    parents.stream().anyMatch(parentId -> isSuccessful(nodeResults.get(parentId)));
            case ALWAYS -> true;
        };
    }

    private boolean isTerminal(InstanceStatus status) {
        return status != null && status.isFinished();
    }

    private boolean isSuccessful(InstanceStatus status) {
        return status == InstanceStatus.SUCCESS || status == InstanceStatus.SKIPPED;
    }
}
