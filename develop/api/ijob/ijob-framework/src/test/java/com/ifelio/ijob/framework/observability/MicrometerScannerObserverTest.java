package com.ifelio.ijob.framework.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class MicrometerScannerObserverTest {
    @Test
    void shouldExposeBatchSaturationItemsAndRunDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerScannerObserver observer = new MicrometerScannerObserver(registry);

        observer.recordBatch("schedule", "due", 100, 100);
        observer.recordItem("schedule", "due", true);
        observer.recordItem("schedule", "due", false);
        observer.recordRun("schedule", true, Duration.ofMillis(25));

        assertEquals(
                100.0,
                registry.get("ijob.scanner.batch.size")
                        .tag("scanner", "schedule")
                        .tag("queue", "due")
                        .gauge()
                        .value());
        assertEquals(
                1.0,
                registry.get("ijob.scanner.batch.saturated")
                        .tag("scanner", "schedule")
                        .tag("queue", "due")
                        .gauge()
                        .value());
        assertEquals(
                1.0,
                registry.get("ijob.scanner.items").tag("outcome", "success").counter().count());
        assertEquals(
                1.0,
                registry.get("ijob.scanner.items").tag("outcome", "failure").counter().count());
        assertNotNull(registry.get("ijob.scanner.run").tag("outcome", "success").timer());
    }
}
