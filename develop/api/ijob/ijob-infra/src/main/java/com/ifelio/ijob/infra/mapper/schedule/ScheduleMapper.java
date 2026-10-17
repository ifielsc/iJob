package com.ifelio.ijob.infra.mapper.schedule;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.schedule.SchedulePageQuery;
import com.ifelio.ijob.infra.entity.schedule.Schedule;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleMapper {
    List<Schedule> selectList(SchedulePageQuery query);

    Schedule selectById(Long id);

    Schedule selectByFireClaimToken(String fireClaimToken);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    List<Schedule> selectListByTargetId(
            @Param("targetType") TargetType targetType, @Param("targetId") Long targetId);

    List<Long> selectIdsForTrigger(@Param("limit") int limit);

    Long selectDueForTriggerForUpdate(@Param("id") Long id);

    int claimDueForTrigger(@Param("id") Long id, @Param("fireClaimToken") String fireClaimToken);

    int insert(Schedule schedule);

    int updateById(Schedule schedule);

    int updateEnabledById(Schedule schedule);

    int updateFireTimeIfClaimed(
            @Param("schedule") Schedule schedule,
            @Param("expectedNextFireTime") LocalDateTime expectedNextFireTime,
            @Param("fireClaimToken") String fireClaimToken);

    int disableByTargetId(
            @Param("targetType") TargetType targetType, @Param("targetId") Long targetId);

    int countByCalendarIds(@Param("calendarIds") List<Long> calendarIds);

    int deleteById(Long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
