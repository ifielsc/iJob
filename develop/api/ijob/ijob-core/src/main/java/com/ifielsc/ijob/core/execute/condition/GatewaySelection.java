package com.ifielsc.ijob.core.execute.condition;

import java.util.Set;

public record GatewaySelection(Set<Long> selectedEdgeIds, boolean reused) {
    public GatewaySelection {
        selectedEdgeIds = Set.copyOf(selectedEdgeIds);
    }
}
