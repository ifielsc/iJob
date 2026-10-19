// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.diagnostics;

import com.ifielsc.ijob.contract.workbench.WorkbenchDTO.NodeConfiguration;

import java.time.LocalDateTime;
import java.util.List;

/** One bounded category at a database-clock observation; hasMore is not an exact total. */
public record DiagnosticsDTO(
        DiagnosticCategory category,
        LocalDateTime observedAt,
        String timeZone,
        String businessZone,
        int limit,
        int queueDelaySeconds,
        boolean hasMore,
        List<DiagnosticItemDTO> items,
        NodeConfiguration node) {}
