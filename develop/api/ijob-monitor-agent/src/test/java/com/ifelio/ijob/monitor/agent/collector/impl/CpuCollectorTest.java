package com.ifelio.ijob.monitor.agent.collector.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CpuCollectorTest {

    @Test
    void shouldConvertRatioToPercentageOnce() {
        assertEquals(50D, CpuCollector.percentage(0.5D));
        assertEquals(12.35D, CpuCollector.percentage(0.12345D));
    }
}
