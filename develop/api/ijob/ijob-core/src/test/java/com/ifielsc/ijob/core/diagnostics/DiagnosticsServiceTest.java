// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticItemDTO;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

class DiagnosticsServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);

    @Test
    void usesDatabaseClockAndBoundedDefaultWithoutCountQuery() {
        var service =
                new DiagnosticsService(
                        (category, now, cutoff) -> {
                            assertEquals(DiagnosticCategory.LEASE_EXPIRED, category);
                            assertEquals(NOW, now);
                            assertEquals(NOW.minusSeconds(300), cutoff);
                            assertEquals(21, PageHelper.getLocalPage().getPageSize());
                            assertFalse(PageHelper.getLocalPage().isCount());
                            return List.of();
                        },
                        () -> NOW,
                        new AppProperties());
        var result = service.query(null, null);
        assertEquals(NOW, result.observedAt());
        assertFalse(result.hasMore());
        assertEquals(20, result.limit());
        assertEquals("UNKNOWN", result.node().clusterWorkerHealth());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void returnsAtMostLimitAndReportsMoreWithoutInventingTotal() {
        var service =
                new DiagnosticsService(
                        (category, now, cutoff) ->
                                IntStream.range(0, 3)
                                        .mapToObj(i -> new DiagnosticItemDTO())
                                        .toList(),
                        () -> NOW,
                        new AppProperties());
        var result = service.query(DiagnosticCategory.COMMAND_EXPIRED, 2);
        assertEquals(2, result.items().size());
        assertTrue(result.hasMore());
        assertEquals(DiagnosticCategory.COMMAND_EXPIRED, result.category());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void rejectsInvalidLimitsBeforeReadingDatabase() {
        var service =
                new DiagnosticsService(
                        (c, n, q) -> {
                            throw new AssertionError();
                        },
                        () -> {
                            throw new AssertionError();
                        },
                        new AppProperties());
        for (int limit : new int[] {0, -1, 101, Integer.MAX_VALUE}) {
            assertThrows(BusinessException.class, () -> service.query(null, limit));
        }
    }

    @Test
    void failedReadClearsThreadLocalPaginationAndDoesNotReturnEmptySuccess() {
        var service =
                new DiagnosticsService(
                        (c, n, q) -> {
                            throw new IllegalStateException("fixture");
                        },
                        () -> NOW,
                        new AppProperties());
        assertThrows(IllegalStateException.class, () -> service.query(null, 100));
        assertNull(PageHelper.getLocalPage());
    }
}
