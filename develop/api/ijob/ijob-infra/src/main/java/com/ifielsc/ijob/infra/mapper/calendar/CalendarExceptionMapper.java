// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.calendar;

import com.ifielsc.ijob.infra.entity.calendar.CalendarException;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CalendarExceptionMapper {
    List<CalendarException> selectByCalendarId(@Param("calendarId") Long calendarId);

    int insert(CalendarException exception);

    int deleteByCalendarId(@Param("calendarId") Long calendarId);
}
