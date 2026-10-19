package com.ifielsc.ijob.contract.job;

import java.util.List;

public record JobMetadataDTO(
        int maxNodes,
        int maxEdges,
        int maxNestingDepth,
        int maxConditionDepth,
        int maxConditionNodes,
        int maxConditionBytes,
        int maxInValues,
        int maxPriority,
        boolean defaultEnabled,
        List<String> gatewayModes,
        List<String> executeConditions,
        List<String> failStrategies,
        List<String> conditionOperators) {}
