package com.ifelio.ijob.contract.schedule;

import com.ifelio.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifelio.ijob.contract.enums.ScheduleType;
import com.ifelio.ijob.contract.enums.TargetType;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ScheduleUpsertRequest {
    private TargetType targetType;
    private Long targetId;
    private ScheduleType scheduleType;
    private String cronExpr;
    private Integer intervalSec;
    private Long calendarId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean enabled;
    private ScheduleMisfirePolicy misfirePolicy;
    private Integer misfireGraceSec;
    private Integer maxCatchupCount;
    private Integer maxQueueDelaySec;
    private Map<String, Object> variables;
    private com.ifelio.ijob.contract.parameter.ParameterForm parameterForm;
}
