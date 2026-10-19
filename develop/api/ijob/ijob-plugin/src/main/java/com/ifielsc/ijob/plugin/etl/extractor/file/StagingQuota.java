package com.ifielsc.ijob.plugin.etl.extractor.file;

import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;

final class StagingQuota {
    private final EtlTaskConfig.ExecutionConfig config;
    private long rows;
    private long cells;
    private long textChars;

    StagingQuota(EtlTaskConfig.ExecutionConfig config) {
        this.config = config;
    }

    void accept(DataRecord record) {
        int fields = record.getFields().size();
        if (rows >= config.getMaxRecords()) {
            throw new IllegalArgumentException("ETL staging row limit exceeded");
        }
        if (fields > config.getMaxRecordFields()) {
            throw new IllegalArgumentException("ETL staging field limit exceeded");
        }
        cells =
                addWithin(
                        cells,
                        fields,
                        config.getMaxStagingCells(),
                        "ETL staging cell limit exceeded");
        for (Object value : record.getFields().values()) {
            if (value == null) {
                continue;
            }
            String text = value.toString();
            if (text.length() > config.getMaxTextFieldChars()) {
                throw new IllegalArgumentException("ETL staging text field limit exceeded");
            }
            textChars =
                    addWithin(
                            textChars,
                            text.length(),
                            config.getMaxExpandedTextChars(),
                            "ETL staging expanded text limit exceeded");
        }
        rows++;
    }

    long rows() {
        return rows;
    }

    long cells() {
        return cells;
    }

    long textChars() {
        return textChars;
    }

    private long addWithin(long current, long amount, long limit, String message) {
        if (amount > limit || current > limit - amount) {
            throw new IllegalArgumentException(message);
        }
        return current + amount;
    }
}
