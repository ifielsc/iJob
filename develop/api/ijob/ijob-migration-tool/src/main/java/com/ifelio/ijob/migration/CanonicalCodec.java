package com.ifelio.ijob.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Calendar;
import java.util.TimeZone;

final class CanonicalCodec {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private CanonicalCodec() {}

    static JsonNode read(ResultSet rows, int index, BundleManifest.LogicalType type, int jdbcType)
            throws SQLException {
        return switch (type) {
            case TEXT -> text(rows.getString(index));
            case NUMBER -> number(rows.getBigDecimal(index), rows.wasNull());
            case BOOLEAN -> bool(rows, index);
            case TIMESTAMP -> timestamp(rows, index, jdbcType);
            case DATE -> date(rows, index, jdbcType);
            case TIME -> time(rows, index, jdbcType);
            case BINARY -> binary(rows.getBytes(index), rows.wasNull());
        };
    }

    static String value(JsonNode node) {
        return node == null || node.isNull() ? null : node.textValue();
    }

    static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }

    private static JsonNode text(String value) {
        // Oracle has no distinct empty VARCHAR/CLOB value. The portable V25 contract therefore
        // normalizes empty text to SQL NULL for every source product.
        return value == null || value.isEmpty() ? NullNode.instance : TextNode.valueOf(value);
    }

    private static JsonNode number(BigDecimal value, boolean wasNull) {
        if (wasNull || value == null) {
            return NullNode.instance;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return TextNode.valueOf(normalized.toPlainString());
    }

    private static JsonNode bool(ResultSet rows, int index) throws SQLException {
        boolean value = rows.getBoolean(index);
        return rows.wasNull() ? NullNode.instance : TextNode.valueOf(Boolean.toString(value));
    }

    private static JsonNode timestamp(ResultSet rows, int index, int jdbcType) throws SQLException {
        if (isTextType(jdbcType)) {
            String raw = rows.getString(index);
            if (raw == null) {
                return NullNode.instance;
            }
            LocalDateTime value = LocalDateTime.parse(raw.replace(' ', 'T'));
            value = value.withNano((value.getNano() / 1_000_000) * 1_000_000);
            return TextNode.valueOf(TIMESTAMP.format(value));
        }
        Timestamp value = rows.getTimestamp(index, utcCalendar());
        if (value == null) {
            return NullNode.instance;
        }
        LocalDateTime truncated =
                LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC)
                        .withNano(
                                (LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC)
                                                        .getNano()
                                                / 1_000_000)
                                        * 1_000_000);
        return TextNode.valueOf(TIMESTAMP.format(truncated));
    }

    private static JsonNode date(ResultSet rows, int index, int jdbcType) throws SQLException {
        if (isTextType(jdbcType)) {
            String raw = rows.getString(index);
            return raw == null
                    ? NullNode.instance
                    : TextNode.valueOf(LocalDate.parse(raw).toString());
        }
        Date value = rows.getDate(index, utcCalendar());
        LocalDate date =
                value == null
                        ? null
                        : Instant.ofEpochMilli(value.getTime())
                                .atOffset(ZoneOffset.UTC)
                                .toLocalDate();
        return date == null ? NullNode.instance : TextNode.valueOf(date.toString());
    }

    private static JsonNode time(ResultSet rows, int index, int jdbcType) throws SQLException {
        if (isTextType(jdbcType)) {
            String raw = rows.getString(index);
            if (raw == null) {
                return NullNode.instance;
            }
            LocalTime value = LocalTime.parse(raw);
            value = value.withNano((value.getNano() / 1_000_000) * 1_000_000);
            return TextNode.valueOf(TIME.format(value));
        }
        Time value = rows.getTime(index, utcCalendar());
        LocalTime time =
                value == null
                        ? null
                        : Instant.ofEpochMilli(value.getTime())
                                .atOffset(ZoneOffset.UTC)
                                .toLocalTime();
        if (time == null) {
            return NullNode.instance;
        }
        time = time.withNano((time.getNano() / 1_000_000) * 1_000_000);
        return TextNode.valueOf(TIME.format(time));
    }

    private static JsonNode binary(byte[] value, boolean wasNull) {
        return wasNull || value == null
                ? NullNode.instance
                : TextNode.valueOf(Base64.getEncoder().encodeToString(value));
    }

    private static boolean isTextType(int jdbcType) {
        return jdbcType == java.sql.Types.CHAR
                || jdbcType == java.sql.Types.VARCHAR
                || jdbcType == java.sql.Types.LONGVARCHAR
                || jdbcType == java.sql.Types.NCHAR
                || jdbcType == java.sql.Types.NVARCHAR
                || jdbcType == java.sql.Types.LONGNVARCHAR
                || jdbcType == java.sql.Types.CLOB
                || jdbcType == java.sql.Types.NCLOB;
    }

    static byte[] lineBytes(String json) {
        return (json + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
