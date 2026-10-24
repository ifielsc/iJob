// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.contract.resource;

import java.util.List;
import java.util.Map;

public final class SqlPreviewDTO {
    private SqlPreviewDTO() {}

    public record Request(
            String requestId,
            Long resourceId,
            String sql,
            String mode,
            Map<String, Object> variables,
            Map<String, Object> parameters,
            Integer maxRows) {}

    public record Column(String name, String type) {}

    public record Result(
            String status,
            List<Column> columns,
            List<List<String>> rows,
            long elapsedMillis,
            String truncated,
            String sqlState,
            Integer position,
            String traceId,
            Integer vendorCode,
            String databaseMessage) {}
}
