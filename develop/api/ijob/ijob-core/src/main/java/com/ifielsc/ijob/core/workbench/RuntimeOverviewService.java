// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.workbench.RuntimeOverviewDTO;
import com.ifielsc.ijob.contract.workbench.RuntimeOverviewDTO.Node;
import com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper;
import com.ifielsc.ijob.infra.mapper.workbench.RuntimeNodeRow;
import com.ifielsc.ijob.infra.mapper.workbench.WorkbenchMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeOverviewService {
    private final WorkbenchMapper mapper;
    private final DatabaseClockMapper clock;
    private final AppProperties properties;
    private final ObjectMapper json;

    public RuntimeOverviewDTO query() {
        var now = clock.currentTimestamp();
        long stale = properties.getRuntime().getMaintenance().getStaleRunTimeout().toSeconds();
        var mode = properties.getRuntime().getMode();
        if (!mode.isPersistent())
            return new RuntimeOverviewDTO(
                    now,
                    stale,
                    mode.name(),
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    null,
                    false,
                    List.of(),
                    List.of());
        List<RuntimeNodeRow> latest;
        PageHelper.startPage(1, 501, false);
        try {
            latest = mapper.selectRuntimeNodes(false);
        } finally {
            PageHelper.clearPage();
        }
        var nodes = latest.stream().limit(500).map(r -> node(r, now, stale)).toList();
        List<RuntimeNodeRow> history;
        PageHelper.startPage(1, 50, false);
        try {
            history = mapper.selectRuntimeNodes(true);
        } finally {
            PageHelper.clearPage();
        }
        return new RuntimeOverviewDTO(
                now,
                stale,
                mode.name(),
                count(nodes, "ONLINE"),
                Long.toString(
                        nodes.stream()
                                .filter(
                                        n ->
                                                n.status().equals("ONLINE")
                                                        && Arrays.asList(n.roles().split(","))
                                                                .contains("WORKER"))
                                .count()),
                count(nodes, "STALE"),
                count(nodes, "STARTING"),
                Long.toString(mapper.countQueuedWork(now)),
                Long.toString(mapper.countClaimedWork(now)),
                Long.toString(mapper.countExpiredWork(now)),
                mapper.selectOldestQueuedAt(now),
                latest.size() > 500,
                nodes,
                history.stream().map(r -> node(r, now, stale)).toList());
    }

    private String count(List<Node> rows, String status) {
        return Long.toString(rows.stream().filter(n -> n.status().equals(status)).count());
    }

    Node node(RuntimeNodeRow r, LocalDateTime now, long stale) {
        boolean active = List.of("STARTING", "RUNNING", "DRAINING").contains(r.getStatus());
        boolean fresh =
                r.getHeartbeatAt() != null && r.getHeartbeatAt().isAfter(now.minusSeconds(stale));
        String status =
                active && !fresh
                        ? "STALE"
                        : "RUNNING".equals(r.getStatus()) ? "ONLINE" : r.getStatus();
        String health = "UNKNOWN", count = null, capacity = null, failures = null;
        LocalDateTime scan = null, renew = null;
        boolean schedule = false;
        if (!Arrays.asList(r.getRoles().split(",")).contains("WORKER")) health = "NOT_WORKER";
        else if (!active || !fresh) health = "UNAVAILABLE";
        try {
            if (r.getTelemetry() != null && active && fresh) {
                var t = json.readTree(r.getTelemetry());
                var measured = LocalDateTime.parse(t.path("measuredAt").asText());
                if (measured.isAfter(now.minusSeconds(stale))
                        && !measured.isAfter(now.plusSeconds(5))
                        && r.getRunId().equals(t.path("runId").asText())) {
                    schedule = t.path("scheduleEnabled").asBoolean();
                    if (!health.equals("NOT_WORKER")) {
                        health =
                                t.path("healthy").asBoolean()
                                        ? (t.path("active").asInt() > 0 ? "BUSY" : "AVAILABLE")
                                        : "DEGRADED";
                        count = t.path("active").asText();
                        capacity = t.path("capacity").asText();
                        failures = t.path("renewalFailures").asText();
                        scan =
                                t.path("lastScanAt").isTextual()
                                        ? LocalDateTime.parse(t.path("lastScanAt").asText())
                                        : null;
                        renew =
                                t.path("lastRenewAt").isTextual()
                                        ? LocalDateTime.parse(t.path("lastRenewAt").asText())
                                        : null;
                    }
                }
            }
        } catch (Exception ignored) {
            health = "UNKNOWN";
        }
        return new Node(
                r.getRunId(),
                r.getInstanceName(),
                r.getRoles(),
                r.getWorkerGroups(),
                status,
                r.getStartedAt(),
                r.getHeartbeatAt(),
                r.getStoppedAt(),
                health,
                count,
                capacity,
                failures,
                scan,
                renew,
                schedule);
    }
}
