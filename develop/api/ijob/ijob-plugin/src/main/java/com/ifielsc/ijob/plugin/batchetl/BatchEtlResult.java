package com.ifielsc.ijob.plugin.batchetl;

import java.util.List;

public record BatchEtlResult(
        long extracted, long inserted, long updated, long skipped, List<TableResult> tables) {
    public record TableResult(
            String sourceTable,
            String targetTable,
            long extracted,
            long inserted,
            long updated,
            long skipped) {}
}
