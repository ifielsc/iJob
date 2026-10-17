package com.ifelio.ijob.core.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.core.retention.RuntimeDataRetentionService.RetentionCutoffs;
import com.ifelio.ijob.core.retention.RuntimeDataRetentionService.RetentionSummary;
import com.ifelio.ijob.infra.mapper.runtime.RuntimeDataRetentionMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class RuntimeDataRetentionServiceTest {
    @Test
    void previewShouldOnlyCountWithoutDeleting() {
        List<String> calls = new ArrayList<>();
        RuntimeDataRetentionService service = new RuntimeDataRetentionService(mapper(calls));

        RetentionSummary summary = service.preview(cutoffs());

        assertEquals(new RetentionSummary(11, 12, 13, 14), summary);
        assertEquals(
                List.of(
                        "countTerminalTriggersBefore",
                        "countTerminalNotificationLogsBefore",
                        "countDeletableEventsBefore",
                        "countTerminalAppRunsBefore"),
                calls);
    }

    @Test
    void cleanupShouldDeleteInstanceTreesBeforeTheirTriggers() {
        List<String> calls = new ArrayList<>();
        RuntimeDataRetentionService service = new RuntimeDataRetentionService(mapper(calls));

        RetentionSummary summary = service.cleanupBatch(cutoffs(), 100);

        assertEquals(new RetentionSummary(2, 3, 2, 4), summary);
        assertEquals(
                List.of(
                        "selectTerminalTriggerIdsBefore",
                        "deleteGatewayDecisionsByTriggerIds",
                        "deleteRootInstancesByTriggerIds",
                        "deleteTriggersByIds",
                        "deleteTerminalNotificationLogsBefore",
                        "selectDeletableEventIdsBefore",
                        "deleteEventsByIds",
                        "deleteTerminalAppRunsBefore"),
                calls);
    }

    private RuntimeDataRetentionMapper mapper(List<String> calls) {
        return (RuntimeDataRetentionMapper)
                Proxy.newProxyInstance(
                        RuntimeDataRetentionMapper.class.getClassLoader(),
                        new Class<?>[] {RuntimeDataRetentionMapper.class},
                        (proxy, method, arguments) -> {
                            calls.add(method.getName());
                            return switch (method.getName()) {
                                case "countTerminalTriggersBefore" -> 11L;
                                case "countTerminalNotificationLogsBefore" -> 12L;
                                case "countDeletableEventsBefore" -> 13L;
                                case "countTerminalAppRunsBefore" -> 14L;
                                case "selectTerminalTriggerIdsBefore" -> List.of(1L, 2L);
                                case "selectDeletableEventIdsBefore" -> List.of(3L, 4L);
                                case "deleteGatewayDecisionsByTriggerIds" -> 3;
                                case "deleteRootInstancesByTriggerIds" -> 2;
                                case "deleteTriggersByIds", "deleteEventsByIds" -> 2;
                                case "deleteTerminalNotificationLogsBefore" -> 3;
                                case "deleteTerminalAppRunsBefore" -> 4;
                                default ->
                                        throw new UnsupportedOperationException(method.getName());
                            };
                        });
    }

    private RetentionCutoffs cutoffs() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new RetentionCutoffs(cutoff, cutoff, cutoff, cutoff);
    }
}
