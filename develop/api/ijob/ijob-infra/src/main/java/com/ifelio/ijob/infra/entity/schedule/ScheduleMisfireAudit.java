package com.ifelio.ijob.infra.entity.schedule;

import com.ifelio.ijob.contract.enums.ScheduleMisfirePolicy;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleMisfireAudit {
    @AutoId(IdNamespace.SCHEDULE_MISFIRE_AUDIT)
    private Long id;

    private Long scheduleId;
    private String fireClaimToken;
    private ScheduleMisfirePolicy policy;
    private LocalDateTime observedAt;
    private LocalDateTime firstDueTime;
    private LocalDateTime lastDueTime;
    private Integer dueCount;
    private Boolean countCapped;
    private Integer createdCount;
    private Integer skippedCount;
    private LocalDateTime createTime;
}
