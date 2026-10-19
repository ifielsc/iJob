package com.ifielsc.ijob.core.schedule;

import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;

import java.time.LocalDateTime;
import java.util.List;

public record ScheduleFirePlan(
        ScheduleMisfirePolicy policy,
        boolean misfire,
        LocalDateTime firstDueTime,
        LocalDateTime lastDueTime,
        List<LocalDateTime> fireTimes,
        LocalDateTime nextFireTime,
        int dueCount,
        int skippedCount,
        boolean countCapped) {}
