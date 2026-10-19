package com.ifielsc.ijob.contract.calendar;

import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class CalendarListDTO {
    private Long id;
    private String name;
    private Set<DayOfWeek> workdays;
    private String description;
    private LocalDateTime updateTime;
}
