package com.ifelio.ijob.plugin.common.jdbc;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class JdbcValueKey {
    private final List<Object> values;

    private JdbcValueKey(List<?> values) {
        List<Object> normalized = new ArrayList<>(values.size());
        for (Object value : values) {
            normalized.add(normalize(value));
        }
        this.values = Collections.unmodifiableList(normalized);
    }

    public static JdbcValueKey of(Object value) {
        return new JdbcValueKey(Collections.singletonList(value));
    }

    public static JdbcValueKey ofValues(List<?> values) {
        return new JdbcValueKey(values);
    }

    public static boolean valuesEqual(Object left, Object right) {
        return of(left).equals(of(right));
    }

    private static Object normalize(Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException ignored) {
                return new SpecialNumber(number.getClass().getName(), number.toString());
            }
        }
        if (value instanceof byte[] bytes) {
            return ByteBuffer.wrap(bytes.clone()).asReadOnlyBuffer();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof ZonedDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value;
    }

    @Override
    public boolean equals(Object value) {
        return value instanceof JdbcValueKey key && values.equals(key.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    private record SpecialNumber(String type, String value) {}
}
