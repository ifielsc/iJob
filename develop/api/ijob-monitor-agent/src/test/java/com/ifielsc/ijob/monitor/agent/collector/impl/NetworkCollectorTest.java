package com.ifielsc.ijob.monitor.agent.collector.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NetworkCollectorTest {

    @Test
    void rateShouldHandleNormalCounterDelta() {
        assertEquals(2_000L, NetworkCollector.rate(3_000L, 1_000L, 1_000L));
    }

    @Test
    void rateShouldIgnoreCounterResetAndInvalidInterval() {
        assertEquals(0L, NetworkCollector.rate(10L, 20L, 1_000L));
        assertEquals(0L, NetworkCollector.rate(20L, 10L, 0L));
    }

    @Test
    void rateShouldSaturateInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, NetworkCollector.rate(Long.MAX_VALUE, 0L, 1L));
    }
}
