// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.calendar;

import com.ifielsc.ijob.contract.enums.CalendarDateType;
import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class CalendarException {
    @AutoId(IdNamespace.CALENDAR_EXCEPTION)
    private Long id;

    private Long calendarId;
    private LocalDate date;
    private CalendarDateType type;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
