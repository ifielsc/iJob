package com.ifielsc.ijob.contract.calendar;

import com.ifielsc.ijob.contract.enums.CalendarDateType;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CalendarExceptionDTO {
    private Long id;
    private LocalDate date;
    private CalendarDateType type;
}
