package com.ifelio.ijob.core.execute.condition;

import com.ifelio.ijob.core.execute.graph.JobExecutionNode;
import com.ifelio.ijob.core.execute.graph.JobExecutionPlan;
import com.ifelio.ijob.infra.entity.runtime.Instance;

import java.util.Map;

@FunctionalInterface
public interface GatewayDecisionResolver {
    GatewaySelection select(
            Long jobInstanceId,
            JobExecutionNode gateway,
            JobExecutionPlan plan,
            Map<Long, Instance> nodeInstances,
            boolean forceReevaluate);
}
