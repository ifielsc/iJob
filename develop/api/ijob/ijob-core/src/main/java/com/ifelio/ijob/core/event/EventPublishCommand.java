package com.ifelio.ijob.core.event;

import com.ifelio.ijob.contract.enums.EventSourceType;

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
