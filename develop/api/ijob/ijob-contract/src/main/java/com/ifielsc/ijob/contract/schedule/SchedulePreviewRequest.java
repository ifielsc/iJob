package com.ifielsc.ijob.contract.schedule;

import com.ifielsc.ijob.contract.enums.ScheduleType;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulePreviewRequest {
    private ScheduleType scheduleType;
    private String cronExpr;
    private Integer intervalSec;
    private Long calendarId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime fromTime;
    private Integer count;
}
