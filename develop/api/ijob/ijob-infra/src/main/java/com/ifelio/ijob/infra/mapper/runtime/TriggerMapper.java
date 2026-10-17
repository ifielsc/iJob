package com.ifelio.ijob.infra.mapper.runtime;

import com.ifelio.ijob.contract.enums.TriggerStatus;
import com.ifelio.ijob.contract.runtime.TriggerDTO;
import com.ifelio.ijob.contract.runtime.TriggerPageQuery;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.entity.runtime.Trigger;

import org.apache.ibatis.annotations.Param;

public interface TriggerMapper {
    Trigger selectById(Long id);

    Trigger selectByIdempotencyKey(String idempotencyKey);

    java.util.List<TriggerDTO> selectDtoList(TriggerPageQuery query);

    int insert(Trigger trigger);

    java.util.List<Long> selectWaitingIds(int limit);

    int updateStatusIfCurrent(
            @Param("id") Long id,
            @Param("expectedStatus") TriggerStatus expectedStatus,
            @Param("status") TriggerStatus status,
            @Param("message") String message,
            @Param("errorCode") String errorCode,
            @Param("messageKey") String messageKey,
            @Param("messageArgs") String messageArgs,
            @Param("permit") ExecutionFencePermit permit);
}
