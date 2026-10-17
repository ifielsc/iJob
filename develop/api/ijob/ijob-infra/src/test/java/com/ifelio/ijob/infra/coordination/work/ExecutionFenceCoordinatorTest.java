package com.ifelio.ijob.infra.coordination.work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class ExecutionFenceCoordinatorTest {
    @Test
    void shouldExposeOnlyTheActiveClaimOrExplicitSuspendedScope() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.STANDALONE);
        ExecutionFenceCoordinator coordinator = new ExecutionFenceCoordinator(properties);
        TriggerWorkClaim claim =
                new TriggerWorkClaim(7L, "node-a", "claim-a", 11L, 1, 3, LocalDateTime.now());

        assertThrows(ExecutionOwnershipLostException.class, () -> coordinator.requirePermit(7L));
        coordinator.activate(claim);
        assertEquals(11L, coordinator.requirePermit(7L).fenceToken());
        coordinator.deactivate(claim);
        coordinator.withSuspendedAccess(
                new SuspendedWorkPermit(7L, 11L),
                () -> {
                    assertEquals(
                            ExecutionFencePermit.Mode.SUSPENDED,
                            coordinator.requirePermit(7L).mode());
                    assertEquals(11L, coordinator.requirePermit(7L).fenceToken());
                });
        assertThrows(ExecutionOwnershipLostException.class, () -> coordinator.requirePermit(7L));
    }

    @Test
    void clusterShouldEnforceFencingWhileDevelopmentDoesNot() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(AppProperties.RuntimeMode.CLUSTER);
        ExecutionFenceCoordinator coordinator = new ExecutionFenceCoordinator(properties);

        assertTrue(coordinator.isEnforced());

        properties.getRuntime().setMode(AppProperties.RuntimeMode.DEVELOPMENT);
        assertFalse(coordinator.isEnforced());
    }
}
