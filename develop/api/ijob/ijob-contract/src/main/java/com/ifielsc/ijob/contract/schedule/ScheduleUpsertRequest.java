package com.ifielsc.ijob.contract.schedule;

import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifielsc.ijob.contract.enums.ScheduleType;
import com.ifielsc.ijob.contract.enums.TargetType;

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
    private com.ifielsc.ijob.contract.parameter.ParameterForm parameterForm;
}
