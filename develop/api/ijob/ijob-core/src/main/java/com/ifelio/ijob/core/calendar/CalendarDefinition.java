package com.ifelio.ijob.core.calendar;

import com.ifelio.ijob.contract.enums.CalendarDateType;
import com.ifelio.ijob.infra.entity.calendar.Calendar;

import java.time.LocalDate;
import java.util.Map;

public record CalendarDefinition(Calendar calendar, Map<LocalDate, CalendarDateType> exceptions) {
    public CalendarDefinition {
        exceptions = Map.copyOf(exceptions);
    }
}
