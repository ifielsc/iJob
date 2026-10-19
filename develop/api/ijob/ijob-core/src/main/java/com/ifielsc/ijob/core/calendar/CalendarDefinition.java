// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.calendar;

import com.ifielsc.ijob.contract.enums.CalendarDateType;
import com.ifielsc.ijob.infra.entity.calendar.Calendar;

import java.time.LocalDate;
import java.util.Map;

public record CalendarDefinition(Calendar calendar, Map<LocalDate, CalendarDateType> exceptions) {
    public CalendarDefinition {
        exceptions = Map.copyOf(exceptions);
    }
}
