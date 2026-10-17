package com.ifelio.ijob.core.execute;

import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.core.job.definition.JobExecutionSpec;

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
