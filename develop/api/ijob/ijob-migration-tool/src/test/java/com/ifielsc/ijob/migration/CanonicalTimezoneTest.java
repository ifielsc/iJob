package com.ifielsc.ijob.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

class CanonicalTimezoneTest {
    @Test
    void nativeAndTextTimestampsDatesAndTimesUseUtcIndependentlyOfJvmZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : new String[] {"UTC", "Asia/Shanghai", "America/New_York"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                check(
                        BundleManifest.LogicalType.TIMESTAMP,
                        Types.TIMESTAMP,
                        "2026-03-08T01:59:59.123",
                        java.sql.Timestamp.from(Instant.parse("2026-03-08T01:59:59.123Z")));
                check(
                        BundleManifest.LogicalType.DATE,
                        Types.DATE,
                        "2026-03-08",
                        new java.sql.Date(Instant.parse("2026-03-08T00:00:00Z").toEpochMilli()));
                check(
                        BundleManifest.LogicalType.TIME,
                        Types.TIME,
                        "23:59:59.123",
                        new java.sql.Time(
                                Instant.parse("1970-01-01T23:59:59.123Z").toEpochMilli()));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private void check(
            BundleManifest.LogicalType type, int jdbcType, String text, java.util.Date value)
            throws Exception {
        ResultSet rows =
                (ResultSet)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ResultSet.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getString") ? text : value);
        assertEquals(text, CanonicalCodec.read(rows, 1, type, jdbcType).textValue());
        assertEquals(text, CanonicalCodec.read(rows, 1, type, Types.VARCHAR).textValue());
        AtomicReference<java.util.Date> bound = new AtomicReference<>();
        PreparedStatement statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    bound.set((java.util.Date) args[1]);
                                    return null;
                                });
        var bind =
                JdbcMigrationService.class.getDeclaredMethod(
                        "bind",
                        PreparedStatement.class,
                        int.class,
                        com.fasterxml.jackson.databind.JsonNode.class,
                        BundleManifest.LogicalType.class,
                        int.class);
        bind.setAccessible(true);
        bind.invoke(null, statement, 1, TextNode.valueOf(text), type, jdbcType);
        assertEquals(value.getTime(), bound.get().getTime());
    }
}
