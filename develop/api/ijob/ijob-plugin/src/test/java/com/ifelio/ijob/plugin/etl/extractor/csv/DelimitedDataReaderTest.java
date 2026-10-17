package com.ifelio.ijob.plugin.etl.extractor.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.DataRecord;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class DelimitedDataReaderTest {
    @Test
    void malformedUtf8MustFailRatherThanReplaceOrSkip() {
        EtlTaskConfig.SourceConfig config = new EtlTaskConfig.SourceConfig();
        config.setHeader(false);
        try (DelimitedDataReader reader =
                new DelimitedDataReader(
                        new ByteArrayInputStream(new byte[] {(byte) 0xff, (byte) 0xfe}),
                        config,
                        1000)) {
            assertThrows(IllegalStateException.class, reader::hasNext);
        }
    }

    @Test
    void shouldReadQuotedDelimiterAndNewline() {
        String source = "id,name,remark\r\n" + "1,\"Alice, A\",\"line1\nline2\"\r\n";
        EtlTaskConfig.SourceConfig config = csvConfig(true);

        try (DelimitedDataReader reader =
                new DelimitedDataReader(
                        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                        config,
                        1000)) {
            DataRecord record = reader.next();

            assertEquals("1", record.get("id"));
            assertEquals("Alice, A", record.get("name"));
            assertEquals("line1\nline2", record.get("remark"));
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void shouldGenerateHeadersWhenHeaderIsAbsent() {
        EtlTaskConfig.SourceConfig config = csvConfig(false);

        try (DelimitedDataReader reader =
                new DelimitedDataReader(
                        new ByteArrayInputStream("a,b\n".getBytes(StandardCharsets.UTF_8)),
                        config,
                        1000)) {
            DataRecord record = reader.next();

            assertEquals("a", record.get("F1"));
            assertEquals("b", record.get("F2"));
        }
    }

    private EtlTaskConfig.SourceConfig csvConfig(boolean header) {
        EtlTaskConfig.SourceConfig config = new EtlTaskConfig.SourceConfig();
        config.setCharset("UTF-8");
        config.setDelimiter(",");
        config.setHeader(header);
        return config;
    }
}
