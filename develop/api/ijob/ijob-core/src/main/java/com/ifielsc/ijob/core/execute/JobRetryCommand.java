// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute;

import com.ifielsc.ijob.core.execute.graph.JobExecutionPlan;
import com.ifielsc.ijob.core.job.definition.JobExecutionSpec;

import java.util.Objects;
import java.util.Set;

public record JobRetryCommand(
        Long instanceId, JobExecutionSpec spec, JobExecutionPlan plan, Set<Long> retryNodeIds) {
    public JobRetryCommand {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(plan, "plan");
        retryNodeIds = Set.copyOf(retryNodeIds);
    }
}
