package com.ifelio.ijob.plugin.etl.extractor.regex;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.reader.DataReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexStreamDataReader implements DataReader {
    private final BufferedReader reader;
    private final EtlTaskConfig.SourceConfig config;
    private final Pattern pattern;
    private Matcher currentMatcher;
    private DataRecord current;

    public RegexStreamDataReader(InputStream input, EtlTaskConfig.SourceConfig config) {
        this.reader =
                new BufferedReader(
                        new InputStreamReader(
                                input, Charset.forName(config.getCharset()).newDecoder()));
        this.config = config;
        this.pattern = Pattern.compile(config.getPattern());
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return true;
        }
        try {
            if (currentMatcher != null && currentMatcher.find()) {
                current = map(currentMatcher);
                return true;
            }
            String value;
            while ((value = readUnit()) != null) {
                currentMatcher = pattern.matcher(value);
                if (!currentMatcher.find()) {
                    continue;
                }
                current = map(currentMatcher);
                return true;
            }
            return false;
        } catch (IOException exception) {
            throw new IllegalStateException("ETL regex source read failed", exception);
        }
    }

    private String readUnit() throws IOException {
        if (!"BLOCK".equalsIgnoreCase(config.getRegexMode())) {
            return reader.readLine();
        }
        StringBuilder block = null;
        int lines = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (block == null) {
                if (config.getBlockStart() != null && !line.matches(config.getBlockStart())) {
                    continue;
                }
                block = new StringBuilder();
            }
            if (++lines > config.getMaxBlockLines()) {
                throw new IllegalArgumentException("ETL regex block line limit exceeded");
            }
            if (block.length() + line.length() + 1 > config.getMaxBlockChars()) {
                throw new IllegalArgumentException("ETL regex block character limit exceeded");
            }
            if (!block.isEmpty()) {
                block.append('\n');
            }
            block.append(line);
            if (config.getBlockEnd() == null || line.matches(config.getBlockEnd())) {
                return block.toString();
            }
        }
        if (block != null) {
            throw new IllegalArgumentException("ETL regex source has an unclosed block");
        }
        return null;
    }

    private DataRecord map(Matcher matcher) {
        DataRecord record = new DataRecord();
        for (EtlTaskConfig.RegexFieldConfig field : config.getRegexFields()) {
            String value;
            try {
                value = group(matcher, field.getGroup());
            } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
                throw new IllegalArgumentException(
                        "ETL regex group is invalid: " + field.getGroup(), exception);
            }
            record.put(field.getField(), value == null ? field.getDefaultValue() : value);
        }
        return record;
    }

    private String group(Matcher matcher, String group) {
        if (group.chars().allMatch(Character::isDigit)) {
            return matcher.group(Integer.parseInt(group));
        }
        return matcher.group(group);
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

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Task cleanup is best effort after the primary operation.
        }
    }
}
