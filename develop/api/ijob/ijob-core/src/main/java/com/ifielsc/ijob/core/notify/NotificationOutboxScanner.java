// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.observability.CoordinationObserver;
import com.ifielsc.ijob.common.runtime.ApplicationRuntimeState;
import com.ifielsc.ijob.infra.coordination.notification.NotificationOutboxItem;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "app.notification.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationOutboxScanner {
    private final NotificationOutboxService outboxService;
    private final NotifyDispatcher notifyDispatcher;
    private final ApplicationRuntimeState runtimeState;
    private final AppProperties appProperties;
    private final CoordinationObserver coordinationObserver;
    private final String leaseOwner = "notification:" + UUID.randomUUID();
    private final ScheduledExecutorService leaseRenewer =
            Executors.newSingleThreadScheduledExecutor(
                    runnable ->
                            Thread.ofPlatform()
                                    .daemon()
                                    .name("notification-outbox-renew")
                                    .unstarted(runnable));

    public NotificationOutboxScanner(
            NotificationOutboxService outboxService,
            NotifyDispatcher notifyDispatcher,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties) {
        this(outboxService, notifyDispatcher, runtimeState, appProperties, Optional.empty());
    }

    @Autowired
    public NotificationOutboxScanner(
            NotificationOutboxService outboxService,
            NotifyDispatcher notifyDispatcher,
            ApplicationRuntimeState runtimeState,
            AppProperties appProperties,
            Optional<CoordinationObserver> coordinationObserver) {
        this.outboxService = outboxService;
        this.notifyDispatcher = notifyDispatcher;
        this.runtimeState = runtimeState;
        this.appProperties = appProperties;
        this.coordinationObserver = coordinationObserver.orElse(CoordinationObserver.NOOP);
    }

    @Scheduled(fixedDelayString = "${app.notification.outbox.scan-interval:1s}")
    public void scan() {
        if (!runtimeState.isReady()
                || !appProperties.getRuntime().getRoles().contains(RuntimeRole.WORKER)) {
            return;
        }
        for (Long id : outboxService.listDispatchableIds()) {
            deliver(id);
        }
    }

    private void deliver(Long id) {
        NotificationOutboxItem item = outboxService.claim(id, leaseOwner);
        if (item == null) {
            return;
        }
        if (item.getAttemptCount() != null && item.getAttemptCount() > 1) {
            coordinationObserver.recordEvent("outbox", "takeover");
        }
        AtomicBoolean ownershipLost = new AtomicBoolean();
        long renewMillis =
                Math.max(
                        1L,
                        appProperties.getNotification().getOutbox().getRenewInterval().toMillis());
        ScheduledFuture<?> renewal =
                leaseRenewer.scheduleAtFixedRate(
                        () -> renew(item, ownershipLost),
                        renewMillis,
                        renewMillis,
                        TimeUnit.MILLISECONDS);
        java.util.Map<String, String> correlation = new java.util.HashMap<>();
        correlation.put("traceId", "outbox-" + item.getId());
        correlation.put("outboxId", String.valueOf(item.getId()));
        correlation.put("instanceId", String.valueOf(item.getInstanceId()));
        correlation.put("attempt", String.valueOf(item.getAttemptCount()));
        try (var ignored = new com.ifielsc.ijob.common.diagnostic.DiagnosticContext(correlation)) {
            NotifyDispatchResult result =
                    notifyDispatcher.dispatch(
                            toConfigSnapshot(item), toMessage(item), toDispatchContext(item));
            if (ownershipLost.get()) {
                coordinationObserver.recordEvent("outbox", "fence-rejected");
                log.warn("notification outbox ownership lost, id={}", item.getId());
            } else if (result.status() == NotifyDispatchStatus.DELIVERED) {
                if (!outboxService.complete(item)) {
                    coordinationObserver.recordEvent("outbox", "fence-rejected");
                    log.warn("notification outbox completion rejected, id={}", item.getId());
                }
            } else if (!outboxService.retry(item, result)) {
                coordinationObserver.recordEvent("outbox", "fence-rejected");
                log.warn("notification outbox retry rejected, id={}", item.getId());
            } else {
                coordinationObserver.recordEvent(
                        "outbox",
                        result.failure() != null && !result.failure().isRetryable()
                                ? "rejected"
                                : "retry");
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "notification outbox delivery failed, id={}, failureType={}",
                    item.getId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
            if (!ownershipLost.get()) {
                try {
                    if (outboxService.retry(item, NotifyDispatchStatus.DELIVERY_FAILED)) {
                        coordinationObserver.recordEvent("outbox", "retry");
                    }
                } catch (RuntimeException persistenceFailure) {
                    log.warn(
                            "notification outbox failure could not be recorded, id={},"
                                    + " failureType={}",
                            item.getId(),
                            com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(
                                    persistenceFailure));
                }
            }
        } finally {
            renewal.cancel(false);
        }
    }

    private void renew(NotificationOutboxItem item, AtomicBoolean ownershipLost) {
        try {
            if (!outboxService.renew(item)) {
                ownershipLost.set(true);
                coordinationObserver.recordEvent("outbox", "fence-rejected");
            }
        } catch (RuntimeException exception) {
            ownershipLost.set(true);
            coordinationObserver.recordEvent("outbox", "lease-renewal-error");
            log.warn(
                    "notification outbox renewal failed, id={}, failureType={}",
                    item.getId(),
                    com.ifielsc.ijob.common.diagnostic.SafeDiagnostic.describe(exception));
        }
    }

    private NotifyMessage toMessage(NotificationOutboxItem item) {
        return NotifyMessage.builder()
                .title(item.getTitle())
                .subject(item.getSubject())
                .content(item.getContent())
                .contentVersion(item.getContentVersion())
                .build();
    }

    private NotifyConfig toConfigSnapshot(NotificationOutboxItem item) {
        NotifyConfig config = new NotifyConfig();
        config.setId(item.getNotifyConfigId());
        config.setType(item.getNotifyType());
        config.setConfig(item.getConfigSnapshot());
        config.setEnabled(item.getConfigEnabled());
        return config;
    }

    private NotifyDispatchContext toDispatchContext(NotificationOutboxItem item) {
        return NotifyDispatchContext.builder()
                .sourceType(item.getSourceType())
                .correlationId(item.getCorrelationId())
                .operatorId(item.getOperatorId())
                .targetType(item.getTargetType())
                .targetId(item.getTargetId())
                .instanceId(item.getInstanceId())
                .notifyBindingId(item.getNotifyBindingId())
                .build();
    }

    @PreDestroy
    public void destroy() {
        leaseRenewer.shutdownNow();
    }
}
