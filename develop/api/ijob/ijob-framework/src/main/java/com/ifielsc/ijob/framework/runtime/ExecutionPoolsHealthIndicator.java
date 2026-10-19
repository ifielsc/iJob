// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.framework.config.ExecutionPoolMonitor;
import com.ifielsc.ijob.framework.config.ExecutionPoolSnapshot;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("executionPools")
public class ExecutionPoolsHealthIndicator implements HealthIndicator {
    private final ExecutionPoolMonitor executionPoolMonitor;

    public ExecutionPoolsHealthIndicator(ExecutionPoolMonitor executionPoolMonitor) {
        this.executionPoolMonitor = executionPoolMonitor;
    }

    @Override
    public Health health() {
        List<ExecutionPoolSnapshot> snapshots = executionPoolMonitor.snapshots();
        boolean unavailable =
                snapshots.stream()
                        .anyMatch(snapshot -> snapshot.shutdown() || snapshot.terminated());
        Health.Builder health = unavailable ? Health.down() : Health.up();
        return health.withDetail("registered", snapshots.size())
                .withDetail(
                        "active",
                        snapshots.stream().mapToInt(ExecutionPoolSnapshot::activeCount).sum())
                .withDetail(
                        "queued",
                        snapshots.stream().mapToInt(ExecutionPoolSnapshot::queuedTaskCount).sum())
                .withDetail(
                        "rejected",
                        snapshots.stream()
                                .mapToLong(ExecutionPoolSnapshot::rejectedTaskCount)
                                .sum())
                .build();
    }
}
