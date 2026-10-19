// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.executor;

import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.plugin.datacheck.config.DataCheckItemConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CountQueryExecutor {
    private final JdbcExecutor jdbcExecutor;

    public Long execute(
            Long resourceId, DataCheckItemConfig config, int timeoutSeconds, TaskContext context) {
        String sourceSql = config.getSql().stripTrailing();
        if (sourceSql.endsWith(";")) {
            sourceSql = sourceSql.substring(0, sourceSql.length() - 1);
        }
        String countSql = "select count(*) from (\n" + sourceSql + "\n) ifj_count_source";
        return jdbcExecutor.queryCount(resourceId, countSql, timeoutSeconds, context);
    }
}
