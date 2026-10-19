// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.NotifySendResult;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.infra.entity.notify.NotifySendLog;
import com.ifielsc.ijob.infra.mapper.notify.NotifySendLogMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotifyAuditRecorder {
    private static final int MAX_CORRELATION_ID_LENGTH = 64;
    private static final int MAX_OPERATOR_ID_LENGTH = 128;

    private final NotifySendLogMapper notifySendLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(NotifyDispatchContext context, Long notifyConfigId) {
        NotifyDispatchContext resolved = context == null ? NotifyDispatchContext.system() : context;
        NotifySendLog log = new NotifySendLog();
        log.setSourceType(
                resolved.getSourceType() == null
                        ? NotifySendSource.SYSTEM
                        : resolved.getSourceType());
        log.setCorrelationId(safeText(resolved.getCorrelationId(), MAX_CORRELATION_ID_LENGTH));
        log.setOperatorId(safeText(resolved.getOperatorId(), MAX_OPERATOR_ID_LENGTH));
        setTarget(log, resolved);
        log.setInstanceId(positiveOrNull(resolved.getInstanceId()));
        log.setNotifyBindingId(positiveOrNull(resolved.getNotifyBindingId()));
        log.setNotifyConfigId(notifyConfigId);
        log.setResult(NotifySendResult.STARTED);
        LocalDateTime now = LocalDateTime.now();
        log.setCreateTime(now);
        log.setUpdateTime(now);

        int inserted = notifySendLogMapper.insertStarted(log);
        if (inserted != 1 || log.getId() == null) {
            throw new IllegalStateException("notify audit start was not stored");
        }
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            Long auditId,
            NotifyDispatchResult dispatchResult,
            NotifyAuditFailure failure,
            long durationMs) {
        if (auditId == null || dispatchResult == null) {
            return;
        }

        NotifySendLog log = new NotifySendLog();
        log.setId(auditId);
        log.setNotifyType(dispatchResult.type());
        log.setResult(toSendResult(dispatchResult.status()));
        if (failure != null) {
            log.setFailureCode(failure.name());
            log.setFailureSummary(failure.getSummary());
        }
        log.setDurationMs(Math.max(0L, durationMs));
        log.setUpdateTime(LocalDateTime.now());

        if (notifySendLogMapper.completeIfStarted(log) != 1) {
            throw new IllegalStateException("notify audit was not STARTED");
        }
    }

    private NotifySendResult toSendResult(NotifyDispatchStatus status) {
        return switch (status) {
            case QUEUED ->
                    throw new IllegalArgumentException("Queued notification is not an attempt");
            case DELIVERED -> NotifySendResult.DELIVERED;
            case CONFIG_NOT_FOUND -> NotifySendResult.CONFIG_NOT_FOUND;
            case CONFIG_DISABLED -> NotifySendResult.CONFIG_DISABLED;
            case CHANNEL_UNAVAILABLE -> NotifySendResult.CHANNEL_UNAVAILABLE;
            case DELIVERY_FAILED -> NotifySendResult.DELIVERY_FAILED;
        };
    }

    private void setTarget(NotifySendLog log, NotifyDispatchContext context) {
        TargetType targetType = context.getTargetType();
        Long targetId = positiveOrNull(context.getTargetId());
        if (targetType != null && targetId != null) {
            log.setTargetType(targetType);
            log.setTargetId(targetId);
        }
    }

    private Long positiveOrNull(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private String safeText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            return null;
        }
        return value;
    }
}
