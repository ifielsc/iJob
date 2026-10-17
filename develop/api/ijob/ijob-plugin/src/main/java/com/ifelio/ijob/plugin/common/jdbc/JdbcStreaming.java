package com.ifelio.ijob.plugin.common.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

public final class JdbcStreaming {
    private JdbcStreaming() {}

    public static void configureFetch(
            PreparedStatement statement, DatabaseMetaData metadata, int fetchSize)
            throws SQLException {
        String product = metadata.getDatabaseProductName();
        if (product != null && product.toLowerCase(Locale.ROOT).contains("mysql")) {
            statement.setFetchSize(Integer.MIN_VALUE);
        } else {
            statement.setFetchSize(fetchSize);
        }
    }
}
