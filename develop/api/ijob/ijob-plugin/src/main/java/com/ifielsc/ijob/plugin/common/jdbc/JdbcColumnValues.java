// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.common.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** Reads standard temporal values without passing vendor-specific objects to another driver. */
public final class JdbcColumnValues {
    private JdbcColumnValues() {}

    public static Object read(ResultSet rows, int column, int jdbcType) throws SQLException {
        return switch (jdbcType) {
            case Types.TIMESTAMP -> rows.getTimestamp(column);
            case Types.DATE -> rows.getDate(column);
            case Types.TIME -> rows.getTime(column);
            default -> rows.getObject(column);
        };
    }

    /** Keeps timezone-free SQL temporal values and their full precision in JSON variables. */
    public static Object readVariable(ResultSet rows, int column, int jdbcType)
            throws SQLException {
        Object value = read(rows, column, jdbcType);
        if (value instanceof java.sql.Timestamp timestamp)
            return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        return value;
    }
}
