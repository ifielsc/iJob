package com.ifelio.ijob.contract.calendar;

import com.ifelio.ijob.contract.enums.CalendarDateType;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarExceptionRequest {
    private LocalDate date;
    private CalendarDateType type;
}
