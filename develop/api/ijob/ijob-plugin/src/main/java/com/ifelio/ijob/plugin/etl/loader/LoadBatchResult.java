package com.ifelio.ijob.plugin.etl.loader;

public record LoadBatchResult(
        long loaded, long inserted, long updated, long skipped, long protectedRecords) {
    public static LoadBatchResult loaded(long count) {
        return new LoadBatchResult(count, count, 0, 0, 0);
    }
}
