package com.ifelio.ijob.plugin.etl.extractor.excel;

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.dummyrecord.LastCellOfRowDummyRecord;
import org.apache.poi.hssf.eventusermodel.dummyrecord.MissingCellDummyRecord;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BlankRecord;
import org.apache.poi.hssf.record.BoolErrRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.LabelRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RKRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.hssf.record.StringRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class HssfEventListener implements HSSFListener {
    private final Consumer<List<String>> consumer;
    private final ExcelExtractConfig config;
    private final List<String> sheetNames = new ArrayList<>();
    private FormatTrackingHSSFListener formatListener;
    private SSTRecord sharedStrings;
    private List<String> row;
    private int sheetIndex = -1;
    private boolean activeSheet;
    private boolean matchedSheet;
    private int nextRow;
    private int formulaRow = -1;
    private int formulaColumn = -1;

    public HssfEventListener(Consumer<List<String>> consumer, ExcelExtractConfig config) {
        this.consumer = consumer;
        this.config = config;
    }

    public void setFormatListener(FormatTrackingHSSFListener listener) {
        this.formatListener = listener;
    }

    @Override
    public void processRecord(Record record) {
        if (record instanceof BoundSheetRecord sheet) {
            sheetNames.add(sheet.getSheetname());
            if (sheetNames.size() > config.getMaxSheets()) {
                throw new IllegalArgumentException("ETL Excel sheet limit exceeded");
            }
            return;
        }
        if (record instanceof SSTRecord strings) {
            sharedStrings = strings;
            return;
        }
        if (record instanceof BOFRecord bof && bof.getType() == BOFRecord.TYPE_WORKSHEET) {
            sheetIndex++;
            activeSheet = matchesSheet();
            matchedSheet |= activeSheet;
            nextRow = 0;
            row = null;
            return;
        }
        if (!activeSheet) {
            return;
        }
        if (record instanceof MissingCellDummyRecord missing) {
            put(missing.getRow(), missing.getColumn(), "");
        } else if (record instanceof NumberRecord number) {
            put(number.getRow(), number.getColumn(), formatListener.formatNumberDateCell(number));
        } else if (record instanceof RKRecord number) {
            put(number.getRow(), number.getColumn(), formatListener.formatNumberDateCell(number));
        } else if (record instanceof LabelSSTRecord label) {
            String value =
                    sharedStrings == null
                            ? ""
                            : sharedStrings.getString(label.getSSTIndex()).toString();
            put(label.getRow(), label.getColumn(), value);
        } else if (record instanceof LabelRecord label) {
            put(label.getRow(), label.getColumn(), label.getValue());
        } else if (record instanceof BoolErrRecord bool) {
            put(
                    bool.getRow(),
                    bool.getColumn(),
                    bool.isBoolean() ? Boolean.toString(bool.getBooleanValue()) : "");
        } else if (record instanceof BlankRecord blank) {
            put(blank.getRow(), blank.getColumn(), "");
        } else if (record instanceof FormulaRecord formula) {
            if (formula.hasCachedResultString()) {
                formulaRow = formula.getRow();
                formulaColumn = formula.getColumn();
            } else {
                put(
                        formula.getRow(),
                        formula.getColumn(),
                        formatListener.formatNumberDateCell(formula));
            }
        } else if (record instanceof StringRecord string && formulaRow >= 0) {
            put(formulaRow, formulaColumn, string.getString());
            formulaRow = -1;
            formulaColumn = -1;
        } else if (record instanceof LastCellOfRowDummyRecord last) {
            while (nextRow < last.getRow()) {
                consumer.accept(List.of());
                nextRow++;
            }
            consumer.accept(row == null ? List.of() : row);
            nextRow = last.getRow() + 1;
            row = null;
        }
    }

    public void requireMatchedSheet() {
        if (!matchedSheet) {
            throw new IllegalArgumentException("ETL Excel sheet was not found");
        }
    }

    private boolean matchesSheet() {
        if (config.getSheetName() != null && !config.getSheetName().isBlank()) {
            return sheetIndex < sheetNames.size()
                    && config.getSheetName().equals(sheetNames.get(sheetIndex));
        }
        return (config.getSheetIndex() == null ? 0 : config.getSheetIndex()) == sheetIndex;
    }

    private void put(int rowNumber, int column, String value) {
        if (row == null) {
            row = new ArrayList<>();
        }
        while (row.size() <= column) {
            row.add("");
        }
        row.set(column, value);
    }
}
