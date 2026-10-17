package com.ifelio.ijob.core.execute.graph;

import com.ifelio.ijob.contract.job.DagConditionExpression;

public record JobExecutionEdge(
        Long id,
        Long definitionId,
        Long fromNodeId,
        Long toNodeId,
        String routeKey,
        Integer priority,
        boolean defaultRoute,
        Integer conditionVersion,
        DagConditionExpression condition) {}
