package com.ifelio.ijob.framework.database.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class SqliteLocalDateTypeHandler extends BaseTypeHandler<LocalDate> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, LocalDate value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, FORMATTER.format(value));
    }

    @Override
    public LocalDate getNullableResult(ResultSet result, String columnName) throws SQLException {
        return parse(result.getString(columnName));
    }

    @Override
    public LocalDate getNullableResult(ResultSet result, int columnIndex) throws SQLException {
        return parse(result.getString(columnIndex));
    }

    @Override
    public LocalDate getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private LocalDate parse(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new SQLException("Invalid SQLite date value", exception);
        }
    }
}
