// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.PluginType;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

class LockServiceCompositeTest {
    @Test
    void missingResolverCannotSilentlyDisableConcurrency() {
        assertThrows(
                NullPointerException.class,
                () -> new LockService(null, null, null, null, Optional.empty(), null));
        assertEquals(1, LockService.class.getConstructors().length);
    }

    @Test
    void oldSnapshotMustUseNewCapacityWithoutShrinkingIt() {
        java.util.concurrent.atomic.AtomicReference<List<ConcurrencyRequirement>> current =
                new java.util.concurrent.atomic.AtomicReference<>(
                        List.of(new ConcurrencyRequirement("RESOURCE:1", 1, 1)));
        ConcurrencyRequirementResolver resolver =
                new ConcurrencyRequirementResolver(null, null) {
                    @Override
                    public List<ConcurrencyRequirement> resolve(Long id, PluginType type) {
                        return current.get();
                    }
                };
        LockService service = new LockService(null, null, null, null, Optional.empty(), resolver);
        LockHandle first = service.acquireTask(1L, 1L, PluginType.SQL);
        current.set(List.of(new ConcurrencyRequirement("RESOURCE:1", 2, 2)));
        LockHandle second = service.acquireTask(2L, 1L, PluginType.SQL);
        service.release(second);
        current.set(List.of(new ConcurrencyRequirement("RESOURCE:1", 1, 1)));
        LockHandle stale = service.acquireTask(3L, 1L, PluginType.SQL);
        service.release(first);
        service.release(stale);
        assertEquals(List.of(2), availablePermits(stale));
    }

    @Test
    void laterScopeFailureMustReturnEarlierScopePermits() {
        java.util.concurrent.atomic.AtomicReference<List<ConcurrencyRequirement>> current =
                new java.util.concurrent.atomic.AtomicReference<>(
                        List.of(new ConcurrencyRequirement("Z", 2, 1)));
        ConcurrencyRequirementResolver resolver =
                new ConcurrencyRequirementResolver(null, null) {
                    @Override
                    public List<ConcurrencyRequirement> resolve(Long id, PluginType type) {
                        return current.get();
                    }
                };
        LockService service = new LockService(null, null, null, null, Optional.empty(), resolver);
        LockHandle active = service.acquireTask(1L, 1L, PluginType.SQL);
        current.set(
                List.of(
                        new ConcurrencyRequirement("A", 1, 2),
                        new ConcurrencyRequirement("Z", 1, 2)));
        assertThrows(
                com.ifielsc.ijob.common.exception.BusinessException.class,
                () -> service.acquireTask(2L, 1L, PluginType.SQL));
        current.set(List.of(new ConcurrencyRequirement("A", 1, 3)));
        LockHandle next = service.acquireTask(3L, 1L, PluginType.SQL);
        service.release(next);
        service.release(active);
        assertEquals(List.of(1), availablePermits(next));
    }

    @Test
    void acquiresAndReleasesEveryLocalRequirementAsOneHandle() {
        List<ConcurrencyRequirement> requirements =
                List.of(
                        new ConcurrencyRequirement("PLUGIN:SQL", 4),
                        new ConcurrencyRequirement("RESOURCE:10", 2),
                        new ConcurrencyRequirement("RESOURCE:20", 1),
                        new ConcurrencyRequirement("TASK:1", 1));
        ConcurrencyRequirementResolver resolver =
                new ConcurrencyRequirementResolver(null, null) {
                    @Override
                    public List<ConcurrencyRequirement> resolve(
                            Long taskId, PluginType pluginType) {
                        return requirements;
                    }
                };
        LockService service = new LockService(null, null, null, null, Optional.empty(), resolver);

        LockHandle handle = service.acquireTask(100L, 1L, PluginType.SQL);

        assertEquals(
                List.of("PLUGIN:SQL", "RESOURCE:10", "RESOURCE:20", "TASK:1"),
                handle.localPermits().stream().map(LockHandle.LocalPermit::scopeKey).toList());
        assertEquals(List.of(3, 1, 0, 0), availablePermits(handle));

        service.release(handle);

        assertEquals(List.of(4, 2, 1, 1), availablePermits(handle));
    }

    @Test
    void localPermitWaitShouldHaveABoundedDeadline() {
        AtomicInteger resolutions = new AtomicInteger();
        ConcurrencyRequirementResolver resolver =
                new ConcurrencyRequirementResolver(null, null) {
                    @Override
                    public List<ConcurrencyRequirement> resolve(
                            Long taskId, PluginType pluginType) {
                        resolutions.incrementAndGet();
                        return List.of(new ConcurrencyRequirement("TASK:1", 1));
                    }
                };
        AppProperties properties = new AppProperties();
        properties.getRuntime().getConcurrencyPermit().setWaitTimeout(Duration.ofMillis(10));
        properties.getRuntime().getConcurrencyPermit().setRetryInterval(Duration.ofMillis(1));
        properties.getRuntime().getConcurrencyPermit().setMaxRetryInterval(Duration.ofMillis(1));
        properties.getRuntime().getConcurrencyPermit().setRetryJitter(Duration.ZERO);
        LockService service =
                new LockService(null, null, null, properties, Optional.empty(), resolver);
        LockHandle first = service.acquireTask(100L, 1L, PluginType.SQL);

        assertThrows(
                PermitWaitTimeoutException.class,
                () -> service.acquireTask(101L, 1L, PluginType.SQL));
        assertEquals(2, resolutions.get());

        service.release(first);
    }

    private List<Integer> availablePermits(LockHandle handle) {
        return handle.localPermits().stream()
                .map(permit -> permit.semaphore().availablePermits())
                .toList();
    }
}
