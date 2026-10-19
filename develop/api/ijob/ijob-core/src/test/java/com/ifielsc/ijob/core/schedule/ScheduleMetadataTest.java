// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class ScheduleMetadataTest {
    @Test
    void reportsEffectiveConfigurationInsteadOfFrontendAssumptions() {
        AppProperties properties = new AppProperties();
        properties.getSchedule().setZoneId("America/New_York");
        properties.getSchedule().setMisfirePolicy(AppProperties.ScheduleMisfirePolicy.SKIP);
        properties.getSchedule().setMisfireGrace(Duration.ofSeconds(7));
        properties.getSchedule().setMaxCatchupCount(3);
        properties.getSchedule().setMaxCatchupWindow(Duration.ofHours(2));
        var dto = new ScheduleService(null, null, null, null, null, null, properties).metadata();
        assertEquals("America/New_York", dto.zoneId());
        assertEquals(ScheduleMisfirePolicy.SKIP, dto.defaultMisfirePolicy());
        assertEquals(7, dto.defaultMisfireGraceSec());
        assertEquals(3, dto.defaultMaxCatchupCount());
        assertEquals(7200, dto.maxCatchupWindowSec());
        assertEquals(50, dto.maxPreviewCount());
        assertEquals(31_536_000, dto.maxIntervalSec());
        assertEquals(65_536, dto.maxVariableBytes());
    }
}
