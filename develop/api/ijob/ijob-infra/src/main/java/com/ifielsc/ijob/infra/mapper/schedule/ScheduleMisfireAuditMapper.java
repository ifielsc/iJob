package com.ifielsc.ijob.infra.mapper.schedule;

import com.ifielsc.ijob.infra.entity.schedule.ScheduleMisfireAudit;

public interface ScheduleMisfireAuditMapper {
    int insert(ScheduleMisfireAudit audit);
}
