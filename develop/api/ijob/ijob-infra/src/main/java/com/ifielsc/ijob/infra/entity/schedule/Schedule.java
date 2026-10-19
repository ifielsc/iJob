// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.schedule;

import com.ifielsc.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifielsc.ijob.contract.enums.ScheduleType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Schedule {
    @AutoId(IdNamespace.SCHEDULE)
    private Long id;

    private long revision;
    private TargetType targetType;
    private Long targetId;
    private String targetName;
    private ScheduleType scheduleType;
    private String cronExpr;
    private Integer intervalSec;
    private Long calendarId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean enabled;
    private LocalDateTime lastFireTime;
    private LocalDateTime nextFireTime;
    private ScheduleMisfirePolicy misfirePolicy;
    private Integer misfireGraceSec;
    private Integer maxCatchupCount;
    private Integer maxQueueDelaySec;
    private String fireClaimToken;
    private LocalDateTime fireClaimedAt;
    private String variables;
    private String parameterForm;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
