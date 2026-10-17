package com.ifelio.ijob.infra.coordination.command;

import com.ifelio.ijob.contract.enums.RuntimeCommandType;

public record RuntimeCommandClaim(
        String requestId,
        Long instanceId,
        Long rootInstanceId,
        Long triggerId,
        RuntimeCommandType commandType,
        String processingOwner,
        String processingToken,
        long processingFence) {}
