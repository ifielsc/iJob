// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.api.event;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ApiResponse;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventSourceType;
import com.ifielsc.ijob.contract.event.EventPublishRequest;
import com.ifielsc.ijob.contract.event.EventReceiptDTO;
import com.ifielsc.ijob.core.auth.AuthSession;
import com.ifielsc.ijob.core.event.EventPublishCommand;
import com.ifielsc.ijob.core.event.EventReceiptResult;
import com.ifielsc.ijob.core.event.EventReceiptService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@ConditionalOnProperty(
        prefix = "app.event.ingress",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class EventIngressController {
    private final EventReceiptService eventReceiptService;
    private final AuthSession authSession;

    public EventIngressController(
            EventReceiptService eventReceiptService, AuthSession authSession) {
        this.eventReceiptService = eventReceiptService;
        this.authSession = authSession;
    }

    @com.ifielsc.ijob.api.security.ApiAccess(
            com.ifielsc.ijob.contract.auth.AccountPermission.EVENT_PUBLISH)
    @com.ifielsc.ijob.api.security.AuditOperation(
            action = "EVENT_INGRESS_PUBLISH",
            target = "EVENT_INGRESS",
            fields = "",
            accepted = true)
    @PostMapping
    public ApiResponse<EventReceiptDTO> publish(
            @RequestBody EventPublishRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false)
                    String idempotencyKeyHeader) {
        if (request == null || request.getSourceKey() == null || request.getSourceKey().isBlank()) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Event source key is required");
        }
        String loginId = authSession.currentUser().getLoginId();
        String idempotencyKey = resolveIdempotencyKey(request, idempotencyKeyHeader);
        EventReceiptResult result =
                eventReceiptService.receive(
                        new EventPublishCommand(
                                request.getEventCode(),
                                EventSourceType.HTTP,
                                "HTTP:" + loginId + ":" + request.getSourceKey(),
                                request.getBusinessKey(),
                                request.getCorrelationKey(),
                                idempotencyKey,
                                request.getOccurredAt(),
                                request.getSummary(),
                                request.getPayload()));
        return ApiResponse.success(new EventReceiptDTO(result.eventId(), result.duplicate()));
    }

    public ApiResponse<EventReceiptDTO> publish(EventPublishRequest request) {
        return publish(request, null);
    }

    private String resolveIdempotencyKey(EventPublishRequest request, String idempotencyKeyHeader) {
        String header = trimToNull(idempotencyKeyHeader);
        String body = trimToNull(request.getIdempotencyKey());
        if (header != null && body != null && !header.equals(body)) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT,
                    "Idempotency-Key header does not match request idempotencyKey");
        }
        String resolved = header == null ? body : header;
        if (resolved == null) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Event idempotency key is required");
        }
        return resolved;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
