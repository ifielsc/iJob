// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.extractor.excel;

import lombok.Data;

@Data
public class ExcelExtractConfig {
    private String filePath;

    private String sheetName;

    /** Zero-based sheet index. */
    private Integer sheetIndex = 0;

    /** Zero-based header row index. */
    private Integer headerRow = 0;

    private boolean header = true;

    /** First row to read. */
    private Integer startRow;

    private long maxRows = 10_000_000L;

    private int maxFields = 1000;

    private int maxTextFieldChars = 1_048_576;

    private long maxExpandedTextChars = 268_435_456L;

    private long maxCells = 50_000_000L;

    private int maxSheets = 100;

    /** Excel handles encoding internally; no encoding setting is required. */
}
