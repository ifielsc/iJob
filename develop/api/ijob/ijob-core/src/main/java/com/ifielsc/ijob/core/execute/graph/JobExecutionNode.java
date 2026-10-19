package com.ifielsc.ijob.core.execute.graph;

import com.ifielsc.ijob.contract.enums.ExecuteCondition;
import com.ifielsc.ijob.contract.enums.FailStrategy;
import com.ifielsc.ijob.contract.enums.GatewayMode;
import com.ifielsc.ijob.contract.enums.JobNodeKind;
import com.ifielsc.ijob.contract.enums.TargetType;

public record JobExecutionNode(
        Long id,
        Long definitionId,
        String nodeKey,
        String name,
        JobNodeKind kind,
        GatewayMode gatewayMode,
        TargetType nodeType,
        Long refId,
        Long refDefinitionId,
        Long refTaskDefinitionId,
        ExecuteCondition executeCondition,
        FailStrategy failStrategy) {
    public JobExecutionNode(
            Long id,
            Long definitionId,
            String nodeKey,
            String name,
            TargetType nodeType,
            Long refId,
            Long refDefinitionId,
            Long refTaskDefinitionId,
            ExecuteCondition executeCondition,
            FailStrategy failStrategy) {
        this(
                id,
                definitionId,
                nodeKey,
                name,
                JobNodeKind.EXECUTION,
                null,
                nodeType,
                refId,
                refDefinitionId,
                refTaskDefinitionId,
                executeCondition,
                failStrategy);
    }

    public JobExecutionNode(
            Long id,
            Long definitionId,
            String nodeKey,
            String name,
            TargetType nodeType,
            Long refId,
            ExecuteCondition executeCondition,
            FailStrategy failStrategy) {
        this(
                id,
                definitionId,
                nodeKey,
                name,
                JobNodeKind.EXECUTION,
                null,
                nodeType,
                refId,
                null,
                null,
                executeCondition,
                failStrategy);
    }
}
