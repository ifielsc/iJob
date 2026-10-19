// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.execute.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.TriggerType;
import com.ifielsc.ijob.infra.entity.runtime.Instance;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

class BusinessTimeSnapshotTest {
    @Test
    void crossesBusinessMidnightWithoutChangingUtcAndSurvivesRetries() {
        Trigger trigger = trigger("2026-10-10T16:30:00");
        BusinessTimeSnapshot.capture(trigger, ZoneId.of("Asia/Shanghai"));
        var service = new VariableSnapshotService(new ObjectMapper());
        var first = service.buildTaskInputs(new Instance(), trigger);
        assertEquals("2026-10-10", first.get("ijob.trigger.date"));
        assertEquals("2026-10-11", first.get("ijob.business.date"));
        assertEquals("2026-10-11T00:30:00+08:00", first.get("ijob.business.time"));
        assertEquals("Asia/Shanghai", first.get("ijob.business.zoneId"));
        assertEquals(first, service.buildTaskInputs(new Instance(), trigger));
    }

    @Test
    void distinguishesRepeatedDstHourAndSkipsMissingHourByInstant() {
        Trigger early = trigger("2026-11-01T05:30:00");
        Trigger late = trigger("2026-11-01T06:30:00");
        Trigger spring = trigger("2026-03-08T07:30:00");
        var zone = ZoneId.of("America/New_York");
        BusinessTimeSnapshot.capture(early, zone);
        BusinessTimeSnapshot.capture(late, zone);
        BusinessTimeSnapshot.capture(spring, zone);
        assertEquals("2026-11-01T01:30:00-04:00", early.getBusinessTime());
        assertEquals("2026-11-01T01:30:00-05:00", late.getBusinessTime());
        assertEquals("2026-03-08T03:30:00-04:00", spring.getBusinessTime());
    }

    @Test
    void legacyTriggerDoesNotInventHistoricalBusinessContext() {
        var values =
                new VariableSnapshotService(new ObjectMapper())
                        .buildTaskInputs(new Instance(), trigger("2026-10-10T16:30:00"));
        assertFalse(values.containsKey("ijob.business.date"));
    }

    private Trigger trigger(String utc) {
        Trigger trigger = new Trigger();
        trigger.setId(1L);
        trigger.setTriggerType(TriggerType.SCHEDULE);
        trigger.setTriggerTime(LocalDateTime.parse(utc));
        return trigger;
    }
}
