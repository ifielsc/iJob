package com.ifielsc.ijob.infra.mapper.runtime;

import com.ifielsc.ijob.contract.enums.TriggerStatus;
import com.ifielsc.ijob.contract.runtime.TriggerDTO;
import com.ifielsc.ijob.contract.runtime.TriggerPageQuery;
import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifielsc.ijob.infra.entity.runtime.Trigger;

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
