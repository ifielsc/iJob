package com.ifelio.ijob.infra.mapper.notify;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.notify.NotifyBindingDTO;
import com.ifelio.ijob.infra.entity.notify.NotifyBinding;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotifyBindingMapper {
    List<NotifyBindingDTO> selectDtoListByTarget(
            @Param("targetType") TargetType targetType, @Param("targetId") Long targetId);

    NotifyBindingDTO selectDtoById(@Param("id") Long id);

    NotifyBinding selectById(@Param("id") Long id);

    List<Long> selectIdsForUpdate(@Param("ids") List<Long> ids);

    NotifyBinding selectByTargetAndConfig(
            @Param("targetType") TargetType targetType,
            @Param("targetId") Long targetId,
            @Param("notifyConfigId") Long notifyConfigId);

    List<NotifyBinding> selectListByTargetId(
            @Param("targetType") TargetType targetType, @Param("targetId") Long targetId);

    int insert(NotifyBinding binding);

    int updatePolicyById(NotifyBinding binding);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    int countByNotifyConfigIds(@Param("notifyConfigIds") List<Long> notifyConfigIds);

    int deleteByTargetId(
            @Param("targetType") TargetType targetType, @Param("targetId") Long targetId);

    int deleteByTargetIds(
            @Param("targetType") TargetType targetType, @Param("targetIds") List<Long> targetIds);
}
