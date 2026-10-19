// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.channel.NotifyChannel;
import com.ifielsc.ijob.core.notify.channel.NotifyChannelResult;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class NotifyDispatcher {
    private final NotifyConfigService notifyConfigService;
    private final List<NotifyChannel> channels;
    private final NotifyAuditRecorder notifyAuditRecorder;
    private final NotifyRateLimitService rateLimits;

    public NotifyDispatcher(
            NotifyConfigService configs, List<NotifyChannel> channels, NotifyAuditRecorder audit) {
        this(configs, channels, audit, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotifyDispatcher(
            NotifyConfigService configs,
            List<NotifyChannel> channels,
            NotifyAuditRecorder audit,
            NotifyRateLimitService rateLimits) {
        this.notifyConfigService = configs;
        this.channels = channels;
        this.notifyAuditRecorder = audit;
        this.rateLimits = rateLimits;
    }

    public NotifyDispatchResult dispatch(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        if (notifyConfigId == null || notifyConfigId <= 0) {
            return result(NotifyDispatchStatus.CONFIG_NOT_FOUND, null);
        }

        long startNanos = System.nanoTime();
        DispatchOutcome outcome = dispatchInternal(notifyConfigId, message, context);
        auditPrecondition(outcome, context, notifyConfigId, startNanos);
        return outcome.result();
    }

    public NotifyDispatchResult dispatch(
            NotifyConfig configSnapshot, NotifyMessage message, NotifyDispatchContext context) {
        if (configSnapshot == null || configSnapshot.getId() == null) {
            return result(NotifyDispatchStatus.CONFIG_NOT_FOUND, null);
        }

        long startNanos = System.nanoTime();
        DispatchOutcome outcome = dispatchSnapshotInternal(configSnapshot, message, context);
        auditPrecondition(outcome, context, configSnapshot.getId(), startNanos);
        return outcome.result();
    }

    private DispatchOutcome dispatchSnapshotInternal(
            NotifyConfig configSnapshot, NotifyMessage message, NotifyDispatchContext context) {
        if (configSnapshot.getType() == null || configSnapshot.getConfig() == null) {
            return outcome(
                    NotifyDispatchStatus.CONFIG_NOT_FOUND,
                    null,
                    NotifyAuditFailure.CONFIG_NOT_FOUND);
        }
        if (Boolean.FALSE.equals(configSnapshot.getEnabled())) {
            return outcome(
                    NotifyDispatchStatus.CONFIG_DISABLED,
                    configSnapshot.getType(),
                    NotifyAuditFailure.CONFIG_DISABLED);
        }
        NotifyConfig runtime;
        try {
            runtime = notifyConfigService.decryptSnapshot(configSnapshot);
        } catch (RuntimeException ex) {
            logFailure(configSnapshot.getId(), configSnapshot.getType(), ex);
            return outcome(
                    NotifyDispatchStatus.DELIVERY_FAILED,
                    configSnapshot.getType(),
                    NotifyAuditFailure.CONFIG_LOOKUP_FAILED);
        }
        return dispatchResolved(runtime, message, context);
    }

    private DispatchOutcome dispatchInternal(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        NotifyConfig config;
        try {
            config = notifyConfigService.getNotifyConfig(notifyConfigId);
        } catch (RuntimeException ex) {
            logFailure(notifyConfigId, null, ex);
            return outcome(
                    NotifyDispatchStatus.DELIVERY_FAILED,
                    null,
                    NotifyAuditFailure.CONFIG_LOOKUP_FAILED);
        }
        return dispatchResolved(config, message, context);
    }

    private DispatchOutcome dispatchResolved(
            NotifyConfig config, NotifyMessage message, NotifyDispatchContext context) {
        if (config == null) {
            return outcome(
                    NotifyDispatchStatus.CONFIG_NOT_FOUND,
                    null,
                    NotifyAuditFailure.CONFIG_NOT_FOUND);
        }

        NotifyType type = config.getType();
        if (Boolean.FALSE.equals(config.getEnabled())) {
            return outcome(
                    NotifyDispatchStatus.CONFIG_DISABLED, type, NotifyAuditFailure.CONFIG_DISABLED);
        }
        if (message == null) {
            return outcome(
                    NotifyDispatchStatus.DELIVERY_FAILED, type, NotifyAuditFailure.MESSAGE_INVALID);
        }

        NotifyChannel channel =
                channels.stream()
                        .filter(candidate -> candidate.type() == type)
                        .findFirst()
                        .orElse(null);
        if (channel == null) {
            return outcome(
                    NotifyDispatchStatus.CHANNEL_UNAVAILABLE,
                    type,
                    NotifyAuditFailure.CHANNEL_UNAVAILABLE);
        }

        if (rateLimits != null && type != NotifyType.EMAIL) {
            try {
                long wait = rateLimits.acquire(config);
                if (wait > 0)
                    return new DispatchOutcome(
                            new NotifyDispatchResult(
                                    NotifyDispatchStatus.DELIVERY_FAILED,
                                    type,
                                    NotifyAuditFailure.RATE_WAIT,
                                    null,
                                    wait),
                            NotifyAuditFailure.RATE_WAIT);
            } catch (IllegalArgumentException e) {
                return outcome(
                        NotifyDispatchStatus.DELIVERY_FAILED,
                        type,
                        NotifyAuditFailure.CONFIG_INVALID);
            } catch (RuntimeException e) {
                return outcome(
                        NotifyDispatchStatus.DELIVERY_FAILED,
                        type,
                        NotifyAuditFailure.CHANNEL_EXCEPTION);
            }
        }
        Long auditId = startAudit(context, config.getId());
        long started = System.nanoTime();
        DispatchOutcome outcome;
        try {
            NotifyChannelResult result = channel.send(config, message);
            outcome =
                    result == NotifyChannelResult.DELIVERED
                            ? outcome(NotifyDispatchStatus.DELIVERED, type, null)
                            : outcome(
                                    NotifyDispatchStatus.DELIVERY_FAILED,
                                    type,
                                    NotifyAuditFailure.CHANNEL_REJECTED);
        } catch (NotifyDeliveryException ex) {
            logFailure(config.getId(), type, ex);
            if (rateLimits != null && ex.retryAfterMillis() > 0) {
                try {
                    rateLimits.cooldown(config, ex.retryAfterMillis(), ex.global());
                } catch (RuntimeException e) {
                    logFailure(config.getId(), type, e);
                }
            }
            outcome =
                    new DispatchOutcome(
                            new NotifyDispatchResult(
                                    NotifyDispatchStatus.DELIVERY_FAILED,
                                    type,
                                    ex.failure(),
                                    ex.httpStatus(),
                                    ex.retryAfterMillis()),
                            ex.failure());
        } catch (RuntimeException ex) {
            logFailure(config.getId(), type, ex);
            outcome =
                    outcome(
                            NotifyDispatchStatus.DELIVERY_FAILED,
                            type,
                            NotifyAuditFailure.CHANNEL_EXCEPTION);
        }
        completeAudit(auditId, outcome, elapsedMillis(started));
        return new DispatchOutcome(outcome.result(), outcome.failure(), true);
    }

    private void auditPrecondition(
            DispatchOutcome outcome, NotifyDispatchContext context, Long configId, long started) {
        if (!outcome.audited() && outcome.failure() != NotifyAuditFailure.RATE_WAIT)
            completeAudit(startAudit(context, configId), outcome, elapsedMillis(started));
    }

    private Long startAudit(NotifyDispatchContext context, Long notifyConfigId) {
        try {
            return notifyAuditRecorder.start(context, notifyConfigId);
        } catch (RuntimeException ex) {
            logAuditFailure("start", notifyConfigId, ex);
            return null;
        }
    }

    private void completeAudit(Long auditId, DispatchOutcome outcome, long durationMs) {
        if (auditId == null) {
            return;
        }
        try {
            notifyAuditRecorder.complete(auditId, outcome.result(), outcome.failure(), durationMs);
        } catch (RuntimeException ex) {
            logAuditFailure("complete", auditId, ex);
        }
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private DispatchOutcome outcome(
            NotifyDispatchStatus status, NotifyType type, NotifyAuditFailure failure) {
        return new DispatchOutcome(new NotifyDispatchResult(status, type, failure), failure);
    }

    private NotifyDispatchResult result(NotifyDispatchStatus status, NotifyType type) {
        return new NotifyDispatchResult(status, type);
    }

    private void logFailure(Long notifyConfigId, NotifyType type, RuntimeException failure) {
        log.warn(
                "notification delivery failed, configId={}, type={}, failureType={}",
                notifyConfigId,
                type,
                com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(failure));
    }

    private void logAuditFailure(String phase, Long referenceId, RuntimeException failure) {
        log.warn(
                "notification audit failed, phase={}, referenceId={}, failureType={}",
                phase,
                referenceId,
                com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(failure));
    }

    private record DispatchOutcome(
            NotifyDispatchResult result, NotifyAuditFailure failure, boolean audited) {
        DispatchOutcome(NotifyDispatchResult result, NotifyAuditFailure failure) {
            this(result, failure, false);
        }
    }
}
