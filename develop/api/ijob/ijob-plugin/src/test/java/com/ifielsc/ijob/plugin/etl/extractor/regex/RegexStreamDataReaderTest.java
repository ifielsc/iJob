package com.ifielsc.ijob.plugin.etl.extractor.regex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class RegexStreamDataReaderTest {
    @Test
    void malformedUtf8MustFailRatherThanReplaceOrSkip() {
        EtlTaskConfig.SourceConfig config = new EtlTaskConfig.SourceConfig();
        config.setPattern("(.*)");
        config.getRegexFields().add(field("value", "1"));
        try (RegexStreamDataReader reader =
                new RegexStreamDataReader(
                        new ByteArrayInputStream(new byte[] {(byte) 0xff, (byte) 0xfe}), config)) {
            assertThrows(IllegalStateException.class, reader::hasNext);
        }
    }

    @Test
    void shouldReturnEveryMatchAndSupportNumericGroups() {
        EtlTaskConfig.SourceConfig config = new EtlTaskConfig.SourceConfig();
        config.setPattern("([A-Z])=(\\d+)");
        config.getRegexFields().add(field("name", "1"));
        config.getRegexFields().add(field("value", "2"));

        try (RegexStreamDataReader reader =
                new RegexStreamDataReader(
                        new ByteArrayInputStream("A=1 B=2\n".getBytes(StandardCharsets.UTF_8)),
                        config)) {
            DataRecord first = reader.next();
            DataRecord second = reader.next();

            assertEquals("A", first.get("name"));
            assertEquals("1", first.get("value"));
            assertEquals("B", second.get("name"));
            assertEquals("2", second.get("value"));
            assertFalse(reader.hasNext());
        }
    }

    private EtlTaskConfig.RegexFieldConfig field(String name, String group) {
        EtlTaskConfig.RegexFieldConfig field = new EtlTaskConfig.RegexFieldConfig();
        field.setField(name);
        field.setGroup(group);
        return field;
    }
}
