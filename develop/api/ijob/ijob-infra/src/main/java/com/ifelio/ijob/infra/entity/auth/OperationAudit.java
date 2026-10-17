package com.ifelio.ijob.infra.entity.auth;

import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mybatis.AutoId;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Persistence-only object. Never serialize into an API response or a diagnostic log. */
@Getter
@Setter
public class OperationAudit {
    @AutoId(IdNamespace.OPERATION_AUDIT)
    private Long id;

    private LocalDateTime occurredAt;
    private String actorLoginId;
    private String actorName;
    private String actionCode;
    private String targetType;
    private String targetId;
    private String resultCode;
    private String traceId;
    private String requestId;
    private String changedFields;
}
