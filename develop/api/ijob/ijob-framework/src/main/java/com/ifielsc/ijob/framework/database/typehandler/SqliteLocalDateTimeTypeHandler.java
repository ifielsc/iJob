// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.framework.database.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public final class SqliteLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {
    public static final DateTimeFormatter FORMATTER =
            new java.time.format.DateTimeFormatterBuilder()
                    .appendPattern("uuuu-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .toFormatter();
    private static final DateTimeFormatter WRITE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, LocalDateTime value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, WRITE_FORMATTER.format(value));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet result, String columnName)
            throws SQLException {
        return parse(result.getString(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet result, int columnIndex) throws SQLException {
        return parse(result.getString(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private LocalDateTime parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new SQLException("Invalid SQLite timestamp value", exception);
        }
    }
}
