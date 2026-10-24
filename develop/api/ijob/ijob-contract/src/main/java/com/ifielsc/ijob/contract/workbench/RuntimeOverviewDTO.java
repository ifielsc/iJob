// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.workbench;

import java.time.LocalDateTime;
import java.util.List;

public record RuntimeOverviewDTO(
        LocalDateTime queriedAt,
        long staleAfterSeconds,
        String mode,
        String online,
        String workers,
        String stale,
        String starting,
        String queued,
        String claimed,
        String expired,
        LocalDateTime oldestQueuedAt,
        boolean truncated,
        List<Node> nodes,
        List<Node> history) {
    public record Node(
            String runId,
            String name,
            String roles,
            String groups,
            String status,
            LocalDateTime startedAt,
            LocalDateTime heartbeatAt,
            LocalDateTime stoppedAt,
            String health,
            String active,
            String capacity,
            String renewalFailures,
            LocalDateTime lastScanAt,
            LocalDateTime lastRenewAt,
            boolean scheduleEnabled) {}
}
