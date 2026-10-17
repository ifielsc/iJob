package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.infra.entity.runtime.EventRecord;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRecordMapper {
    int insert(EventRecord event);

    EventRecord selectById(Long id);

    EventRecord selectBySourceAndIdempotency(
            @Param("sourceKey") String sourceKey, @Param("idempotencyKey") String idempotencyKey);

    EventRecord selectFirstMatching(
            @Param("eventCode") String eventCode,
            @Param("businessKey") String businessKey,
            @Param("correlationKey") String correlationKey,
            @Param("acceptAfter") LocalDateTime acceptAfter);

    List<EventRecord> selectMatchingForWait(
            @Param("eventCode") String eventCode,
            @Param("businessKey") String businessKey,
            @Param("correlationKey") String correlationKey,
            @Param("acceptAfter") LocalDateTime acceptAfter,
            @Param("limit") int limit);
}
