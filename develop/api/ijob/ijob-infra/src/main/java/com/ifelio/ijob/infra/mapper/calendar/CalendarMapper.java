package com.ifelio.ijob.infra.mapper.calendar;

import com.ifelio.ijob.contract.calendar.CalendarPageQuery;
import com.ifelio.ijob.infra.entity.calendar.Calendar;

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
