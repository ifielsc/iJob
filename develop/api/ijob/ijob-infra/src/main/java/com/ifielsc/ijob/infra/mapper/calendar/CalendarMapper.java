// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.calendar;

import com.ifielsc.ijob.contract.calendar.CalendarPageQuery;
import com.ifielsc.ijob.infra.entity.calendar.Calendar;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CalendarMapper {
    List<Calendar> selectList(CalendarPageQuery query);

    Calendar selectById(@Param("id") Long id);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    Calendar selectByName(@Param("name") String name);

    int insert(Calendar calendar);

    int updateById(Calendar calendar);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
