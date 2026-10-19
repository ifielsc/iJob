package com.ifielsc.ijob.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.framework.config.ExecutionPoolMonitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class RuntimeHealthIndicatorTest {
    @Test
    void runtimeShouldBecomeHealthyOnlyWhenReady() {
        ApplicationRuntimeState state = new ApplicationRuntimeState();
        RuntimeHealthIndicator indicator = new RuntimeHealthIndicator(state);

        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());

        state.markReady();
        assertEquals(Status.UP, indicator.health().getStatus());

        state.markDraining();
        assertEquals(Status.OUT_OF_SERVICE, indicator.health().getStatus());
    }

    @Test
    void emptyExecutionPoolRegistryShouldBeHealthyDuringBootstrap() {
        ExecutionPoolsHealthIndicator indicator =
                new ExecutionPoolsHealthIndicator(new ExecutionPoolMonitor());

        assertEquals(Status.UP, indicator.health().getStatus());
    }
}
