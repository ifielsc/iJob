// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("runtime")
public class RuntimeHealthIndicator implements HealthIndicator {
    private final ApplicationRuntimeState runtimeState;

    public RuntimeHealthIndicator(ApplicationRuntimeState runtimeState) {
        this.runtimeState = runtimeState;
    }

    @Override
    public Health health() {
        Health.Builder health = runtimeState.isReady() ? Health.up() : Health.outOfService();
        health.withDetail("phase", runtimeState.phase())
                .withDetail("changedAt", runtimeState.changedAt());
        if (runtimeState.fenceReason() != null) {
            health.withDetail("fenceReason", runtimeState.fenceReason());
        }
        return health.build();
    }
}
