// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.incremental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;

import org.junit.jupiter.api.Test;

class FileMarkerTest {
    @Test
    void exactNumbersAndStringOrderRemainDistinct() {
        assertTrue(EtlWatermark.compare("9007199254740993", "9007199254740992", "LONG") > 0);
        assertTrue(
                EtlWatermark.compare("0.12345678901234567891", "0.12345678901234567890", "DECIMAL")
                        > 0);
        assertTrue(EtlWatermark.compare("10", "9", "STRING") < 0);
        assertTrue(EtlWatermark.compare("10", "9", "LONG") > 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> EtlWatermark.normalize("1e3", "LONG", null, "UTC"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EtlWatermark.normalize("9223372036854775808", "LONG", null, "UTC"));
    }

    @Test
    void parsesDateDirectoriesAndTimestampFilenamesStrictly() {
        var c = config("DATE", "{mark}/orders_*.csv");
        c.getFile().setMarkerLocation(EtlTaskConfig.MarkerLocation.RELATIVE_PATH);
        c.getFile().setMarkerFormat("yyyy/MM/dd");
        assertEquals("2026-09-28", new FileMarkerSelector(c).extract("2026/09/28/orders_a.csv"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileMarkerSelector(c).extract("2026/02/30/orders_a.csv"));
        c.setWatermarkType("TIMESTAMP");
        c.getFile().setMarkerLocation(EtlTaskConfig.MarkerLocation.NAME);
        c.getFile().setExpression("orders_{mark}.csv");
        c.getFile().setMarkerFormat("yyyyMMdd_HHmmss");
        c.getFile().setZoneId("Asia/Shanghai");
        assertEquals(
                "2026-09-28T07:30:00Z",
                new FileMarkerSelector(c).extract("x/orders_20260928_153000.csv"));
    }

    @Test
    void supportsNamedRegexAndRejectsExpensiveMatching() {
        var c = config("LONG", "orders_(?<mark>[0-9]+)_[a-z]+\\.csv");
        c.getFile().setMatchMode(EtlTaskConfig.MatchMode.REGEX);
        assertEquals("1058", new FileMarkerSelector(c).extract("orders_001058_ab.csv"));
        assertNull(new FileMarkerSelector(c).extract("readme.txt"));
        c.getFile().setExpression("(?<mark>(a+)+)b");
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileMarkerSelector(c).extract("a".repeat(2048)));
    }

    @Test
    void lookbackIsTypedAndTimeZonesAreUnambiguous() {
        assertEquals("100", EtlWatermark.subtract("105", "5", "LONG"));
        assertEquals("2026-09-25", EtlWatermark.subtract("2026-09-28", "P3D", "DATE"));
        assertEquals(
                "2026-09-27T00:00:00Z",
                EtlWatermark.subtract("2026-09-28T00:00:00Z", "PT24H", "TIMESTAMP"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EtlWatermark.subtract("ABC009", "3", "STRING"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EtlWatermark.normalize(
                                "2026-11-01T01:30:00", "TIMESTAMP", null, "America/New_York"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        EtlWatermark.normalize(
                                "2026-03-08T02:30:00", "TIMESTAMP", null, "America/New_York"));
    }

    @Test
    void etlCodecPreservesExactDecimalsAndRejectsFractionalIdentifiers() {
        var codec = new PluginConfigCodec(new ObjectMapper());
        var config =
                codec.read(
                        "{\"source\":{\"type\":\"DATABASE\",\"resourceId\":9007199254740993},\"incremental\":{\"initialWatermark\":0.12345678901234567891}}",
                        EtlTaskConfig.class,
                        "ETL");
        assertEquals(9007199254740993L, config.getSource().getResourceId());
        assertEquals(
                "0.12345678901234567891", config.getIncremental().getInitialWatermark().toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read("{\"source\":{\"resourceId\":1.2}}", EtlTaskConfig.class, "ETL"));
    }

    @Test
    void comparesJdbcTimestampsWithPersistedOffsetsAndLocalNanosecondValues() {
        var timestamp = java.sql.Timestamp.valueOf("2026-01-02 00:00:00.123456789");
        var persistedOffset =
                timestamp.toInstant().atOffset(java.time.ZoneOffset.ofHours(8)).toString();
        assertEquals(0, EtlWatermark.compare(timestamp, persistedOffset, "TIMESTAMP"));
        assertEquals(0, EtlWatermark.compare(persistedOffset, timestamp, "TIMESTAMP"));
        assertEquals(
                0,
                EtlWatermark.compare(
                        timestamp, timestamp.toLocalDateTime().toString(), "TIMESTAMP"));
        assertTrue(
                EtlWatermark.compare(timestamp, "2026-01-02T00:00:00.123456788", "TIMESTAMP") > 0);
        assertEquals(
                0,
                EtlWatermark.compare(
                        "2026-01-02T08:00:00+08:00", "2026-01-02T00:00:00Z", "TIMESTAMP"));
    }

    private EtlTaskConfig.IncrementalConfig config(String type, String expression) {
        var c = new EtlTaskConfig.IncrementalConfig();
        c.setWatermarkType(type);
        c.setFile(new EtlTaskConfig.FileIncrementalConfig());
        c.getFile().setExpression(expression);
        return c;
    }
}
