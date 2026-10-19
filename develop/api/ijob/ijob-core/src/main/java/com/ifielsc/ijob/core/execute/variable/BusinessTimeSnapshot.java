package com.ifielsc.ijob.core.execute.variable;

import com.ifielsc.ijob.infra.entity.runtime.Trigger;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Captures trigger business time once; execution and retries never consult the current zone. */
public final class BusinessTimeSnapshot {
    private BusinessTimeSnapshot() {}

    public static void capture(Trigger trigger, ZoneId zone) {
        var time = trigger.getTriggerTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(zone);
        trigger.setBusinessDate(time.toLocalDate().toString());
        trigger.setBusinessTime(time.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        trigger.setBusinessZoneId(zone.getId());
    }
}
