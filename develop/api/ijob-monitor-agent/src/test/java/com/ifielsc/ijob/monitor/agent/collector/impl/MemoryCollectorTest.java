package com.ifielsc.ijob.monitor.agent.collector.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemoryCollectorTest {

    @Test
    void shouldCalculateUsedMemoryPercentageOnce() {
        assertEquals(75D, MemoryCollector.percentage(1_000L, 250L));
        assertEquals(0D, MemoryCollector.percentage(0L, 0L));
    }
}
