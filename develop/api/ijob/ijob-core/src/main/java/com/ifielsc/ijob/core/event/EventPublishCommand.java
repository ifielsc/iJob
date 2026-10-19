// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import com.ifielsc.ijob.contract.enums.EventSourceType;

import java.time.LocalDateTime;

public record EventPublishCommand(
        String eventCode,
        EventSourceType sourceType,
        String sourceKey,
        String businessKey,
        String correlationKey,
        String idempotencyKey,
        LocalDateTime occurredAt,
        String summary,
        Object payload) {}
