// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.retention;

import com.ifielsc.ijob.infra.mapper.runtime.RuntimeDataRetentionMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RuntimeDataRetentionService {
    private final RuntimeDataRetentionMapper retentionMapper;
    private final com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard guard;

    public RuntimeDataRetentionService(RuntimeDataRetentionMapper retentionMapper) {
        this(retentionMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RuntimeDataRetentionService(
            RuntimeDataRetentionMapper retentionMapper,
            com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard guard) {
        this.retentionMapper = retentionMapper;
        this.guard = guard;
    }

    public RetentionSummary preview(RetentionCutoffs cutoffs) {
        return new RetentionSummary(
                retentionMapper.countTerminalTriggersBefore(cutoffs.execution()),
                retentionMapper.countTerminalNotificationLogsBefore(cutoffs.notification()),
                retentionMapper.countDeletableEventsBefore(cutoffs.event()),
                retentionMapper.countTerminalAppRunsBefore(cutoffs.appRun()));
    }

    @Transactional
    public RetentionSummary cleanupBatch(RetentionCutoffs cutoffs, int batchSize) {
        if (batchSize < 1 || batchSize > 1000)
            throw new IllegalArgumentException("Invalid retention batch size");
        if (guard != null)
            guard.lock(com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard.LIFECYCLE);
        List<Long> triggerIds =
                retentionMapper.selectTerminalTriggerIdsBefore(cutoffs.execution(), batchSize);
        if (!triggerIds.isEmpty()) {
            retentionMapper.deleteGatewayDecisionsByTriggerIds(triggerIds);
            retentionMapper.deleteRootInstancesByTriggerIds(triggerIds);
        }
        int deletedTriggers =
                triggerIds.isEmpty() ? 0 : retentionMapper.deleteTriggersByIds(triggerIds);

        int deletedNotifications =
                retentionMapper.deleteTerminalNotificationLogsBefore(
                        cutoffs.notification(), batchSize);

        List<Long> eventIds =
                retentionMapper.selectDeletableEventIdsBefore(cutoffs.event(), batchSize);
        int deletedEvents = eventIds.isEmpty() ? 0 : retentionMapper.deleteEventsByIds(eventIds);

        int deletedAppRuns =
                retentionMapper.deleteTerminalAppRunsBefore(cutoffs.appRun(), batchSize);
        return new RetentionSummary(
                deletedTriggers, deletedNotifications, deletedEvents, deletedAppRuns);
    }

    public record RetentionCutoffs(
            LocalDateTime execution,
            LocalDateTime notification,
            LocalDateTime event,
            LocalDateTime appRun) {}

    public record RetentionSummary(long executions, long notifications, long events, long appRuns) {
        public RetentionSummary plus(RetentionSummary other) {
            return new RetentionSummary(
                    executions + other.executions,
                    notifications + other.notifications,
                    events + other.events,
                    appRuns + other.appRuns);
        }

        public boolean mayHaveMore(int batchSize) {
            return executions >= batchSize
                    || notifications >= batchSize
                    || events >= batchSize
                    || appRuns >= batchSize;
        }

        public static RetentionSummary empty() {
            return new RetentionSummary(0, 0, 0, 0);
        }
    }
}
