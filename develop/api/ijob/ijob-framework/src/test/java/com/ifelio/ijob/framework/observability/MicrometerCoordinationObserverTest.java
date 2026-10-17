package com.ifelio.ijob.framework.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class MicrometerCoordinationObserverTest {
    @Test
    void shouldExposeStateLeaseDelayAndEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerCoordinationObserver observer = new MicrometerCoordinationObserver(registry);

        observer.recordState("outbox", "ready", 7);
        observer.recordLease("runtime-maintenance", true, 3);
        observer.recordDelay("command", Duration.ofMillis(125));
        observer.recordEvent("work", "takeover");
        observer.recordPermitWaiting(2);
        observer.recordPermitWait(Duration.ofMillis(50), "acquired");

        assertEquals(
                7,
                registry.get("ijob.coordination.items")
                        .tag("component", "outbox")
                        .tag("state", "ready")
                        .gauge()
                        .value());
        assertEquals(
                1,
                registry.get("ijob.coordination.lease.active")
                        .tag("lease", "runtime-maintenance")
                        .gauge()
                        .value());
        assertEquals(
                3,
                registry.get("ijob.coordination.lease.fence")
                        .tag("lease", "runtime-maintenance")
                        .gauge()
                        .value());
        assertEquals(
                125,
                registry.get("ijob.coordination.delay.millis")
                        .tag("component", "command")
                        .gauge()
                        .value());
        assertEquals(
                1,
                registry.get("ijob.coordination.events")
                        .tag("component", "work")
                        .tag("event", "takeover")
                        .counter()
                        .count());
        assertEquals(2, registry.get("ijob.concurrency.permit.waiting").gauge().value());
        assertEquals(
                1,
                registry.get("ijob.concurrency.permit.wait.duration")
                        .tag("outcome", "acquired")
                        .timer()
                        .count());
    }
}
