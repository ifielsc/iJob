package com.ifielsc.ijob.plugin.etl.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EtlResult {
    private boolean success;

    private long extractCount;

    private long loadCount;

    private long invalidCount;

    private long insertedCount;

    private long updatedCount;

    private long skippedCount;

    private long protectedCount;

    private String message;

    public static EtlResult success(EtlContext context) {
        return EtlResult.builder()
                .success(true)
                .extractCount(context.getExtractCount())
                .loadCount(context.getLoadCount())
                .invalidCount(context.getValidateErrorCount())
                .insertedCount(context.getInsertCount())
                .updatedCount(context.getUpdateCount())
                .skippedCount(context.getSkipCount())
                .protectedCount(context.getProtectedCount())
                .build();
    }
}
