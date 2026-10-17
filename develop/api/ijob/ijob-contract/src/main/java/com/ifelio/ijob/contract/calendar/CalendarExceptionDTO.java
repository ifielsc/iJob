package com.ifelio.ijob.contract.calendar;

import com.ifelio.ijob.contract.enums.CalendarDateType;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarExceptionDTO {
    private Long id;
    private LocalDate date;
    private CalendarDateType type;
}
