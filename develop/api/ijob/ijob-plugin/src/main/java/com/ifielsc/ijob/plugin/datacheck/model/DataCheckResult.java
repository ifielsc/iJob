// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.datacheck.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DataCheckResult {
    private boolean success;

    private int total;

    private int successCount;

    private int failedCount;

    @Builder.Default private List<DataCheckItemResult> items = new ArrayList<>();

    private String message;

    public static DataCheckResult from(List<DataCheckItemResult> items, boolean taskSuccess) {
        long passed = items.stream().filter(DataCheckItemResult::isSuccess).count();

        return DataCheckResult.builder()
                .success(taskSuccess)
                .items(items)
                .total(items.size())
                .successCount((int) passed)
                .failedCount(items.size() - (int) passed)
                .build();
    }
}
