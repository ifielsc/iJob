package com.ifielsc.ijob.plugin.etl.extractor.csv;

import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.reader.DataReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DelimitedDataReader implements DataReader {
    private final PushbackReader reader;
    private final char delimiter;
    private final Character quote;
    private final Character escape;
    private final int maxFieldChars;
    private List<String> headers;
    private List<String> current;

    public DelimitedDataReader(
            InputStream input, EtlTaskConfig.SourceConfig config, int maxFieldChars) {
        this.reader =
                new PushbackReader(
                        new BufferedReader(
                                new InputStreamReader(
                                        input, Charset.forName(config.getCharset()).newDecoder())),
                        1);
        this.delimiter = config.getDelimiter().charAt(0);
        this.quote = config.getQuote();
        this.escape = config.getEscape();
        this.maxFieldChars = maxFieldChars;
        initialize(config);
    }

    private void initialize(EtlTaskConfig.SourceConfig config) {
        try {
            for (int index = 0; index < config.getSkipLines(); index++) {
                if (readRecord() == null) {
                    return;
                }
            }
            if (config.isHeader()) {
                headers = readRecord();
                validateHeaders(headers);
            }
        } catch (IOException exception) {
            close();
            throw new IllegalStateException(
                    "ETL delimited source could not be initialized", exception);
        }
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }
        try {
            current = readRecord();
            return current != null;
        } catch (IOException exception) {
            throw new IllegalStateException("ETL delimited source read failed", exception);
        }
    }

    @Override
    public DataRecord next() {
        if (!hasNext()) {
            return null;
        }
        List<String> values = current;
        current = null;
        if (headers == null) {
            headers = generatedHeaders(values.size());
        }
        if (values.size() > headers.size()) {
            throw new IllegalArgumentException(
                    "ETL delimited row contains more columns than the header");
        }
        DataRecord record = new DataRecord();
        for (int index = 0; index < headers.size(); index++) {
            record.put(headers.get(index), index < values.size() ? values.get(index) : null);
        }
        return record;
    }

    private List<String> readRecord() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean readAny = false;
        while (true) {
            int value = reader.read();
            if (value < 0) {
                if (!readAny && fields.isEmpty() && field.isEmpty()) {
                    return null;
                }
                if (quoted) {
                    throw new IllegalArgumentException(
                            "ETL delimited source has an unclosed quoted field");
                }
                fields.add(field.toString());
                return fields;
            }
            readAny = true;
            char currentChar = (char) value;
            if (quoted && escape != null && !escape.equals(quote) && currentChar == escape) {
                int escaped = reader.read();
                if (escaped < 0) {
                    throw new IllegalArgumentException(
                            "ETL delimited source has an incomplete escape");
                }
                append(field, (char) escaped);
                continue;
            }
            if (quote != null && currentChar == quote) {
                if (quoted) {
                    int next = reader.read();
                    if (next == quote) {
                        append(field, quote);
                    } else {
                        quoted = false;
                        if (next >= 0) {
                            reader.unread(next);
                        }
                    }
                } else if (field.isEmpty()) {
                    quoted = true;
                } else {
                    append(field, currentChar);
                }
                continue;
            }
            if (!quoted && currentChar == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                continue;
            }
            if (!quoted && (currentChar == '\r' || currentChar == '\n')) {
                if (currentChar == '\r') {
                    int next = reader.read();
                    if (next >= 0 && next != '\n') {
                        reader.unread(next);
                    }
                }
                fields.add(field.toString());
                return fields;
            }
            append(field, currentChar);
        }
    }

    private void append(StringBuilder field, char value) {
        if (field.length() >= maxFieldChars) {
            throw new IllegalArgumentException("ETL delimited field limit exceeded");
        }
        field.append(value);
    }

    private void validateHeaders(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("ETL delimited header is missing");
        }
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !seen.add(value)) {
                throw new IllegalArgumentException(
                        "ETL delimited headers must be non-blank and unique");
            }
        }
    }

    private List<String> generatedHeaders(int size) {
        List<String> result = new ArrayList<>(size);
        for (int index = 1; index <= size; index++) {
            result.add("F" + index);
        }
        return result;
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Task cleanup is best effort after the primary operation.
        }
    }
}
