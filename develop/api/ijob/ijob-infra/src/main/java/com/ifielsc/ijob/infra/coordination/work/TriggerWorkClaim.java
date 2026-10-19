// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.coordination.work;

import java.time.LocalDateTime;

public record TriggerWorkClaim(
        Long triggerId,
        String leaseOwner,
        String claimToken,
        long fenceToken,
        int attemptCount,
        int maxAttempts,
        LocalDateTime leaseDeadline) {}
