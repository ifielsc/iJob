package com.ifelio.ijob.infra.entity.calendar;

import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class Calendar {
    @AutoId(IdNamespace.CALENDAR)
    private Long id;

    private String name;
    private Integer workdaysMask;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public boolean isWorkday(LocalDate date) {
        return WorkdayMask.contains(workdaysMask, date.getDayOfWeek());
    }
}
