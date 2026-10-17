package com.ifelio.ijob.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeMode;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.infra.coordination.maintenance.CoordinationSnapshot;
import com.ifelio.ijob.infra.mapper.coordination.CoordinationSnapshotMapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class CoordinationHealthIndicatorTest {
    @Test
    void readyStandaloneShouldRequireAnActiveMaintenanceLeader() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        ApplicationRuntimeState state = new ApplicationRuntimeState();
        state.markReady();
        CoordinationSnapshot snapshot = new CoordinationSnapshot();
        CoordinationSnapshotMapper mapper = (lease, stale) -> snapshot;
        CoordinationHealthIndicator indicator =
                new CoordinationHealthIndicator(mapper, properties, state);

        assertEquals(Status.DOWN, indicator.health().getStatus());

        snapshot.setMaintenanceLeaderActive(1);
        snapshot.setOutboxDead(2);
        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(true, indicator.health().getDetails().get("attentionRequired"));
    }

    @Test
    void developmentShouldNotQueryPersistentCoordination() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.DEVELOPMENT);
        CoordinationSnapshotMapper mapper =
                (lease, stale) -> {
                    throw new AssertionError("Persistent snapshot must not be queried");
                };
        CoordinationHealthIndicator indicator =
                new CoordinationHealthIndicator(mapper, properties, new ApplicationRuntimeState());

        assertEquals(Status.UP, indicator.health().getStatus());
    }
}
