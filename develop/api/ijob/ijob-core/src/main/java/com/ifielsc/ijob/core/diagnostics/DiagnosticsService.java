// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.diagnostics;

import com.github.pagehelper.PageHelper;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticItemDTO;
import com.ifielsc.ijob.contract.diagnostics.DiagnosticsDTO;
import com.ifielsc.ijob.contract.workbench.WorkbenchDTO.NodeConfiguration;
import com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper;
import com.ifielsc.ijob.infra.mapper.diagnostics.DiagnosticsMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class DiagnosticsService {
    static final int QUEUE_DELAY_SECONDS = 300;
    private final DiagnosticsMapper mapper;
    private final DatabaseClockMapper clock;
    private final AppProperties properties;

    public DiagnosticsService(
            DiagnosticsMapper mapper, DatabaseClockMapper clock, AppProperties properties) {
        this.mapper = mapper;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DiagnosticsDTO query(DiagnosticCategory category, Integer requestedLimit) {
        int limit = requestedLimit == null ? 20 : requestedLimit;
        if (limit < 1 || limit > 100) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        DiagnosticCategory resolved =
                category == null ? DiagnosticCategory.LEASE_EXPIRED : category;
        LocalDateTime now =
                Objects.requireNonNull(clock.currentTimestamp(), "Database clock unavailable");
        List<DiagnosticItemDTO> rows;
        PageHelper.startPage(1, limit + 1, false);
        try {
            rows = mapper.selectItems(resolved, now, now.minusSeconds(QUEUE_DELAY_SECONDS));
        } finally {
            PageHelper.clearPage();
        }
        var runtime = properties.getRuntime();
        var roles = runtime.getRoles();
        return new DiagnosticsDTO(
                resolved,
                now,
                "UTC",
                properties.getSchedule().getZoneId(),
                limit,
                QUEUE_DELAY_SECONDS,
                rows.size() > limit,
                List.copyOf(rows.subList(0, Math.min(limit, rows.size()))),
                new NodeConfiguration(
                        runtime.getMode().name(),
                        roles.stream().map(Enum::name).sorted().toList(),
                        roles.contains(RuntimeRole.WORKER),
                        roles.contains(RuntimeRole.SCHEDULER),
                        properties.getSchedule().isEnabled(),
                        "UNKNOWN"));
    }
}
