package com.ifelio.ijob.plugin.etl.extractor.excel;

import java.util.List;

/** Shared by sequential sheet readers so skipped rows and headers also consume the run budget. */
public final class ExcelReadQuota {
    private final ExcelExtractConfig limits;
    private long rows;
    private long cells;
    private long textChars;

    public ExcelReadQuota(ExcelExtractConfig limits) {
        this.limits = limits;
    }

    public void accept(List<String> row) {
        rows = addWithin(rows, 1, limits.getMaxRows(), "ETL Excel row limit exceeded");
        if (row.size() > limits.getMaxFields()) {
            throw new IllegalArgumentException("ETL Excel field limit exceeded");
        }
        cells = addWithin(cells, row.size(), limits.getMaxCells(), "ETL Excel cell limit exceeded");
        for (String value : row) {
            if (value == null) {
                continue;
            }
            if (value.length() > limits.getMaxTextFieldChars()) {
                throw new IllegalArgumentException("ETL Excel text field limit exceeded");
            }
            textChars =
                    addWithin(
                            textChars,
                            value.length(),
                            limits.getMaxExpandedTextChars(),
                            "ETL Excel expanded text limit exceeded");
        }
    }

    private long addWithin(long current, long amount, long limit, String message) {
        if (amount > limit || current > limit - amount) {
            throw new IllegalArgumentException(message);
        }
        return current + amount;
    }
}
