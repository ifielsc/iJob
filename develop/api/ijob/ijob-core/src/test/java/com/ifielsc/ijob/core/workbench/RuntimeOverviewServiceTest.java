// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.infra.mapper.workbench.RuntimeNodeRow;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class RuntimeOverviewServiceTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
    private final RuntimeOverviewService service =
            new RuntimeOverviewService(null, null, null, new ObjectMapper());

    private RuntimeNodeRow row(String status, long age) {
        var row = new RuntimeNodeRow();
        row.setRunId("run-a");
        row.setInstanceName("node-a");
        row.setRoles("API,WORKER");
        row.setStatus(status);
        row.setHeartbeatAt(now.minusSeconds(age));
        row.setTelemetry(
                "{\"runId\":\"run-a\",\"measuredAt\":\"2026-10-21T10:00\",\"healthy\":true,\"active\":2,\"capacity\":8,\"renewalFailures\":0}");
        return row;
    }

    @Test
    void freshWorkerReportsObservedExecutionAndCapacity() {
        var node = service.node(row("RUNNING", 2), now, 30);
        assertEquals("ONLINE", node.status());
        assertEquals("BUSY", node.health());
        assertEquals("2", node.active());
        assertEquals("8", node.capacity());
    }

    @Test
    void staleHeartbeatCannotReuseHealthyTelemetry() {
        var node = service.node(row("RUNNING", 30), now, 30);
        assertEquals("STALE", node.status());
        assertEquals("UNAVAILABLE", node.health());
        assertNull(node.active());
    }

    @Test
    void stoppedNodeCannotReuseHealthyTelemetry() {
        var node = service.node(row("STOPPED", 2), now, 30);
        assertEquals("STOPPED", node.status());
        assertEquals("UNAVAILABLE", node.health());
    }

    @Test
    void telemetryBelongsToExactRunAndExpiresIndependently() {
        var row = row("RUNNING", 2);
        row.setRunId("new-run");
        assertEquals("UNKNOWN", service.node(row, now, 30).health());
        row.setRunId("run-a");
        row.setHeartbeatAt(now.plusSeconds(59));
        assertEquals("UNKNOWN", service.node(row, now.plusMinutes(1), 30).health());
    }

    @Test
    void missingAndMalformedObservationStayUnknown() {
        var row = row("RUNNING", 2);
        row.setTelemetry(null);
        assertEquals("UNKNOWN", service.node(row, now, 30).health());
        row.setTelemetry("{bad");
        assertEquals("UNKNOWN", service.node(row, now, 30).health());
    }

    @Test
    void apiRoleDoesNotProveExecutionHealth() {
        var row = row("RUNNING", 2);
        row.setRoles("API");
        assertEquals("NOT_WORKER", service.node(row, now, 30).health());
    }
}
