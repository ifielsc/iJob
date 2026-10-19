package com.ifielsc.ijob.plugin.etl.extractor.excel;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.reader.DataReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExcelDataReader implements DataReader {
    private final ExcelStreamReader reader;
    private final ExcelExtractConfig config;
    private final ExcelReadQuota quota;
    private List<String> headers;
    private DataRecord current;
    private long rowIndex;

    public ExcelDataReader(ExcelStreamReader reader, ExcelExtractConfig config) {
        this(reader, config, new ExcelReadQuota(config));
    }

    public ExcelDataReader(
            ExcelStreamReader reader, ExcelExtractConfig config, ExcelReadQuota quota) {
        this.reader = reader;
        this.config = config;
        this.quota = quota;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }
        while (reader.hasNext()) {
            List<String> row = reader.next();
            quota.accept(row);
            long currentIndex = rowIndex++;
            if (config.isHeader() && currentIndex == value(config.getHeaderRow(), 0)) {
                headers = List.copyOf(row);
                validateHeaders(headers);
                continue;
            }
            int firstDataRow =
                    config.getStartRow() == null
                            ? config.isHeader() ? value(config.getHeaderRow(), 0) + 1 : 0
                            : config.getStartRow();
            if (currentIndex < firstDataRow) {
                continue;
            }
            if (row.isEmpty()) {
                continue;
            }
            if (headers == null) {
                headers = generateHeaders(row.size());
            }
            if (row.size() > headers.size()) {
                throw new IllegalArgumentException(
                        "ETL Excel row contains more columns than the header");
            }
            current = map(row);
            return true;
        }
        return false;
    }

    public List<String> getHeaders() {
        return headers == null ? List.of() : List.copyOf(headers);
    }

    private DataRecord map(List<String> row) {
        DataRecord record = new DataRecord();
        for (int index = 0; index < headers.size(); index++) {
            record.put(headers.get(index), index < row.size() ? row.get(index) : null);
        }
        return record;
    }

    @Override
    public DataRecord next() {
        if (!hasNext()) {
            return null;
        }
        DataRecord result = current;
        current = null;
        return result;
    }

    private void validateHeaders(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("ETL Excel header row must not be empty");
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !seen.add(value)) {
                throw new IllegalArgumentException(
                        "ETL Excel headers must be non-blank and unique");
            }
        }
    }

    private List<String> generateHeaders(int size) {
        List<String> result = new ArrayList<>(size);
        for (int index = 1; index <= size; index++) {
            result.add("F" + index);
        }
        return result;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    @Override
    public void close() {
        reader.close();
    }
}
