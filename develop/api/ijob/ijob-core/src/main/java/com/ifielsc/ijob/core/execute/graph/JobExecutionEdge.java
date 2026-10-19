// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.contract.job.DagConditionExpression;

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
