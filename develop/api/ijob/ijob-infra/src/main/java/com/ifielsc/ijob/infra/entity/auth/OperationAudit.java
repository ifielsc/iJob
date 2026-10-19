// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.entity.auth;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;
import com.ifielsc.ijob.infra.mybatis.AutoId;

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
