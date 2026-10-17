package com.ifelio.ijob.core.execute;

import java.util.List;
import java.util.Set;

public record JobRetryPlan(Set<Long> retryNodeIds, List<Long> resetInstanceIds) {
    public JobRetryPlan {
        retryNodeIds = Set.copyOf(retryNodeIds);
        resetInstanceIds = List.copyOf(resetInstanceIds);
    }
}
