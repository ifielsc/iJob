// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.command;

import com.ifielsc.ijob.contract.enums.RuntimeCommandType;

public record RuntimeCommandClaim(
        String requestId,
        Long instanceId,
        Long rootInstanceId,
        Long triggerId,
        RuntimeCommandType commandType,
        String processingOwner,
        String processingToken,
        long processingFence) {}
