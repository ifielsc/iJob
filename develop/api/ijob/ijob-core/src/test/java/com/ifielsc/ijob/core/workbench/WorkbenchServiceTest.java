// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.contract.auth.CurrentUserResponse;
import com.ifielsc.ijob.contract.workbench.WorkbenchFailureDTO;
import com.ifielsc.ijob.contract.workbench.WorkbenchQuery;
import com.ifielsc.ijob.core.auth.AccountRolePolicy;
import com.ifielsc.ijob.infra.mapper.workbench.WorkbenchMapper;
import com.ifielsc.ijob.infra.mapper.workbench.WorkbenchStatusCount;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

class WorkbenchServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 10, 0);

    @Test
    void defaultRangeUsesSuppliedUtcClockAndDoesNotMutateRequest() {
        WorkbenchQuery request = new WorkbenchQuery();
        var query = WorkbenchService.resolve(request, NOW);
        assertEquals(NOW, query.getTo());
        assertEquals(NOW.minusHours(24), query.getFrom());
        assertNull(request.getFrom());
    }

    @Test
    void rangeRequiresBothEndsAndRejectsReverseAndOver31Days() {
        WorkbenchQuery query = new WorkbenchQuery();
        query.setFrom(NOW);
        assertThrows(BusinessException.class, () -> WorkbenchService.resolve(query, NOW));
        query.setTo(NOW);
        assertThrows(BusinessException.class, () -> WorkbenchService.resolve(query, NOW));
        query.setTo(NOW.minusSeconds(1));
        assertThrows(BusinessException.class, () -> WorkbenchService.resolve(query, NOW));
        query.setTo(NOW.plusDays(31));
        assertEquals(query.getTo(), WorkbenchService.resolve(query, NOW).getTo());
        query.setTo(NOW.plusDays(31).plusNanos(1));
        assertThrows(BusinessException.class, () -> WorkbenchService.resolve(query, NOW));
    }

    @Test
    void emptyWindowHasNoSuccessRateAndViewerNeverQueriesApprovals() {
        FakeMapper mapper = new FakeMapper();
        var result =
                new WorkbenchService(mapper, new AppProperties())
                        .query(null, user(AccountRole.VIEWER));
        assertEquals("0", result.total());
        assertEquals(9, result.statuses().size());
        assertNull(result.successRatePercent());
        assertNull(result.pendingApprovals());
        assertFalse(result.approvalsVisible());
        assertNull(mapper.operator);
    }

    @Test
    void denominatorExcludesStoppedSkippedAndUnfinishedAndPreservesLargeCounts() {
        FakeMapper mapper = new FakeMapper();
        mapper.rows =
                List.of(
                        row("SUCCESS", 1),
                        row("FAILED", 1),
                        row("TIMED_OUT", 1),
                        row("STOPPED", 10),
                        row("SKIPPED", 20),
                        row("RUNNING", 9007199254740993L));
        var result =
                new WorkbenchService(mapper, new AppProperties())
                        .query(null, user(AccountRole.OPERATOR));
        assertEquals("9007199254741026", result.total());
        assertEquals("3", result.successRateDenominator());
        assertEquals("33.33", result.successRatePercent());
        assertEquals("reviewer", mapper.operator);
        assertEquals("9007199254740993", result.pendingApprovals());
    }

    @Test
    void configuredApiOnlyIsNotClusterWorkerHealthAndActiveRootsIgnoreWindow() {
        FakeMapper mapper = new FakeMapper();
        AppProperties properties = new AppProperties();
        properties.getRuntime().setRoles(Set.of(AppProperties.RuntimeRole.API));
        properties.getSchedule().setZoneId("America/New_York");
        var result = new WorkbenchService(mapper, properties).query(null, user(AccountRole.VIEWER));
        assertEquals("UTC", result.timeZone());
        assertEquals("America/New_York", result.businessZone());
        assertEquals("UNKNOWN", result.node().clusterWorkerHealth());
        assertFalse(result.node().workerRoleConfigured());
        assertFalse(result.node().schedulerRoleConfigured());
        assertFalse(result.node().scheduleEnabled());
        assertEquals("2", result.currentActiveRoots().get("RUNNING"));
        assertEquals("0", result.total());
    }

    @Test
    void boundedRecentQueryAlwaysClearsThreadPaginationAfterFailure() {
        FakeMapper mapper = new FakeMapper();
        mapper.fail = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        new WorkbenchService(mapper, new AppProperties())
                                .query(null, user(AccountRole.VIEWER)));
        assertNull(PageHelper.getLocalPage());
    }

    private static CurrentUserResponse user(AccountRole role) {
        return new CurrentUserResponse(
                "reviewer",
                "reviewer",
                "Reviewer",
                role,
                AccountRolePolicy.permissions(role),
                false);
    }

    private static WorkbenchStatusCount row(String status, long count) {
        WorkbenchStatusCount row = new WorkbenchStatusCount();
        row.setStatus(status);
        row.setCount(count);
        return row;
    }

    private static final class FakeMapper implements WorkbenchMapper {
        private List<WorkbenchStatusCount> rows = List.of();
        private String operator;
        private boolean fail;

        @Override
        public List<WorkbenchStatusCount> selectWindowStatuses(WorkbenchQuery query) {
            return rows;
        }

        @Override
        public List<WorkbenchStatusCount> selectCurrentActiveRoots() {
            return List.of(row("RUNNING", 2));
        }

        @Override
        public long countPendingApprovals(String operatorId) {
            operator = operatorId;
            return 9007199254740993L;
        }

        @Override
        public List<WorkbenchFailureDTO> selectRecentFailures(WorkbenchQuery query) {
            assertEquals(5, PageHelper.getLocalPage().getPageSize());
            assertFalse(PageHelper.getLocalPage().isCount());
            assertTrue(query.getFrom().isBefore(query.getTo()));
            if (fail) throw new IllegalStateException("simulated database failure");
            return List.of();
        }
    }
}
