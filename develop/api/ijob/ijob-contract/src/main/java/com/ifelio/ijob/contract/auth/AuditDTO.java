package com.ifelio.ijob.contract.auth;

import java.time.LocalDateTime;

public record AuditDTO(
        String id,
        LocalDateTime occurredAt,
        String actorLoginId,
        String actorName,
        String actionCode,
        String targetType,
        String targetId,
        String resultCode,
        String traceId,
        String requestId,
        String changedFields) {}
