package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.contract.enums.EventWaitStatus;
import com.ifelio.ijob.contract.event.EventWaitDTO;
import com.ifelio.ijob.contract.event.EventWaitPageQuery;
import com.ifelio.ijob.infra.entity.runtime.EventWait;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventWaitMapper {
    int insert(EventWait wait);

    int insertOperator(@Param("waitId") Long waitId, @Param("operatorId") String operatorId);

    EventWait selectById(Long id);

    EventWaitDTO selectContext(Long id);

    EventWait selectByInstanceAndAttempt(
            @Param("instanceId") Long instanceId, @Param("attemptNo") int attemptNo);

    EventWait selectLatestByInstance(Long instanceId);

    List<String> selectOperatorIds(Long waitId);

    List<EventWaitDTO> selectApprovalList(
            @Param("query") EventWaitPageQuery query, @Param("operatorId") String operatorId);

    List<EventWait> selectWaitingForEvent(
            @Param("eventCode") String eventCode,
            @Param("businessKey") String businessKey,
            @Param("correlationKey") String correlationKey,
            @Param("arrivedAt") LocalDateTime arrivedAt);

    List<EventWait> selectRecoverableWaitingBatch(@Param("limit") int limit);

    List<EventWait> selectWaitingByRoot(Long rootInstanceId);

    List<EventWait> selectWaitingByInstance(Long instanceId);

    List<EventWait> selectTimedOut(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<Long> selectResolvedRootIds(@Param("limit") int limit);

    int resolveIfWaiting(
            @Param("id") Long id,
            @Param("status") EventWaitStatus status,
            @Param("matchedEventId") Long matchedEventId,
            @Param("decisionBy") String decisionBy,
            @Param("decisionComment") String decisionComment,
            @Param("resolvedAt") LocalDateTime resolvedAt);
}
