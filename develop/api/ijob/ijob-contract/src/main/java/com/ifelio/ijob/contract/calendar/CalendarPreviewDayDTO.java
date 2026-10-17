package com.ifelio.ijob.contract.calendar;

import com.ifelio.ijob.contract.enums.CalendarDateType;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarPreviewDayDTO {
    private LocalDate date;
    private Boolean allowed;
    private CalendarDateType exceptionType;
}
