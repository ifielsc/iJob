package com.ifielsc.ijob.infra.mapper.runtime;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RuntimeDataRetentionMapper {
    long countTerminalTriggersBefore(LocalDateTime cutoff);

    List<Long> selectTerminalTriggerIdsBefore(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    int deleteGatewayDecisionsByTriggerIds(@Param("ids") List<Long> ids);

    int deleteRootInstancesByTriggerIds(@Param("ids") List<Long> ids);

    int deleteTriggersByIds(@Param("ids") List<Long> ids);

    long countTerminalNotificationLogsBefore(LocalDateTime cutoff);

    int deleteTerminalNotificationLogsBefore(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    long countDeletableEventsBefore(LocalDateTime cutoff);

    List<Long> selectDeletableEventIdsBefore(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    int deleteEventsByIds(@Param("ids") List<Long> ids);

    long countTerminalAppRunsBefore(LocalDateTime cutoff);

    int deleteTerminalAppRunsBefore(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
