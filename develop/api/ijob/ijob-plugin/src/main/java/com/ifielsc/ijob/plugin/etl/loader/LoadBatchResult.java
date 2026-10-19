// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.loader;

public record LoadBatchResult(
        long loaded, long inserted, long updated, long skipped, long protectedRecords) {
    public static LoadBatchResult loaded(long count) {
        return new LoadBatchResult(count, count, 0, 0, 0);
    }
}
