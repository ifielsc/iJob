// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.event;

import com.ifielsc.ijob.infra.entity.event.EventBinding;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EventBindingMapper {
    List<EventBinding> selectListByEventCode(@Param("eventCode") String eventCode);

    List<EventBinding> selectListByTarget(
            @Param("targetType") com.ifielsc.ijob.contract.enums.TargetType targetType,
            @Param("targetId") Long targetId);

    EventBinding selectById(Long id);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    EventBinding selectByTargetAndEvent(
            @Param("targetType") com.ifielsc.ijob.contract.enums.TargetType targetType,
            @Param("targetId") Long targetId,
            @Param("eventCode") String eventCode);

    int insert(EventBinding binding);

    int updateEnabledById(EventBinding binding);

    int disableByTargetId(
            @Param("targetType") com.ifielsc.ijob.contract.enums.TargetType targetType,
            @Param("targetId") Long targetId,
            @Param("disabled") boolean disabled,
            @Param("updateTime") java.time.LocalDateTime updateTime);

    int deleteById(Long id);

    int deleteByIds(@Param("ids") List<Long> ids);
}
