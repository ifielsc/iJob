// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.resource.ReferenceSummaryRequest;
import com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper;
import com.ifielsc.ijob.infra.mapper.resource.ResourceMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

class ReferenceSummaryServiceTest {
    @Test
    void referencesAreDeduplicatedBatchedAndBoundedBeforeQuerying() {
        List<List<Long>> calls = new ArrayList<>();
        var service =
                new ReferenceSummaryService(
                        mapper(ResourceMapper.class, calls),
                        mapper(NotifyConfigMapper.class, calls));
        assertTrue(
                service.resolve(new ReferenceSummaryRequest(List.of(1L, 1L, 2L), List.of(3L)))
                        .isEmpty());
        assertEquals(List.of(List.of(1L, 2L), List.of(3L)), calls);
        calls.clear();
        assertTrue(service.resolve(new ReferenceSummaryRequest(null, List.of())).isEmpty());
        assertTrue(calls.isEmpty());
        assertThrows(
                BusinessException.class,
                () -> service.resolve(new ReferenceSummaryRequest(List.of(0L), null)));
        assertThrows(
                BusinessException.class,
                () ->
                        service.resolve(
                                new ReferenceSummaryRequest(
                                        java.util.Collections.nCopies(201, 1L), null)));
        assertTrue(calls.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private <T> T mapper(Class<T> type, List<List<Long>> calls) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (proxy, method, args) -> {
                            assertEquals("selectSummariesByIds", method.getName());
                            calls.add((List<Long>) args[0]);
                            return List.of();
                        });
    }
}
