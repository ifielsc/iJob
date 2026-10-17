package com.ifelio.ijob.contract.schedule;

import com.ifelio.ijob.contract.enums.ScheduleType;

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
