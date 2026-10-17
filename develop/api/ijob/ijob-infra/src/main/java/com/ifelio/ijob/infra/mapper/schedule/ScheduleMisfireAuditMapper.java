package com.ifelio.ijob.infra.mapper.schedule;

import com.ifelio.ijob.infra.entity.schedule.ScheduleMisfireAudit;

public interface ScheduleMisfireAuditMapper {
    int insert(ScheduleMisfireAudit audit);
}
