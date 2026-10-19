// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.coordination;

import com.ifielsc.ijob.contract.enums.RuntimeCommandStatus;
import com.ifielsc.ijob.contract.enums.RuntimeCommandType;
import com.ifielsc.ijob.contract.runtime.RuntimeCommandDTO;
import com.ifielsc.ijob.infra.coordination.command.RuntimeCommand;
import com.ifielsc.ijob.infra.coordination.command.RuntimeCommandCandidate;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RuntimeCommandMapper {
    int insertFromInstance(
            @Param("requestId") String requestId,
            @Param("instanceId") Long instanceId,
            @Param("commandType") RuntimeCommandType commandType,
            @Param("requestedBy") String requestedBy,
            @Param("timeoutMicros") long timeoutMicros);

    RuntimeCommand selectByRequestId(String requestId);

    RuntimeCommandDTO selectDtoByRequestId(String requestId);

    int rejectInitiallyInvalid(@Param("requestId") String requestId);

    int succeedInitiallySatisfied(@Param("requestId") String requestId);

    int requeueWorkForCommand(@Param("requestId") String requestId);

    int activateTriggerForRetry(@Param("requestId") String requestId);

    List<String> selectDispatchableRequestIds(
            @Param("triggerId") Long triggerId,
            @Param("fenceToken") long fenceToken,
            @Param("limit") int limit);

    List<RuntimeCommandCandidate> selectDispatchableOwned(
            @Param("leaseOwner") String leaseOwner, @Param("limit") int limit);

    int claim(
            @Param("requestId") String requestId,
            @Param("triggerId") Long triggerId,
            @Param("leaseOwner") String leaseOwner,
            @Param("processingToken") String processingToken,
            @Param("fenceToken") long fenceToken);

    int finish(
            @Param("requestId") String requestId,
            @Param("processingToken") String processingToken,
            @Param("fenceToken") long fenceToken,
            @Param("status") RuntimeCommandStatus status,
            @Param("resultMessage") String resultMessage);

    int defer(
            @Param("requestId") String requestId,
            @Param("processingToken") String processingToken,
            @Param("fenceToken") long fenceToken,
            @Param("delayMicros") long delayMicros,
            @Param("resultMessage") String resultMessage);

    int succeedCompletedStops(@Param("limit") int limit);

    int expirePending(@Param("limit") int limit);

    int expireByRequestId(@Param("requestId") String requestId);
}
