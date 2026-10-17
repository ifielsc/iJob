package com.ifelio.ijob.plugin.common.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/** Quotes already validated JDBC identifiers using the target database rules. */
public final class JdbcIdentifierQuoter {
    private final String quote;

    private JdbcIdentifierQuoter(String quote) {
        this.quote = quote;
    }

    public static JdbcIdentifierQuoter from(DatabaseMetaData metadata) throws SQLException {
        String value = metadata.getIdentifierQuoteString();
        return new JdbcIdentifierQuoter(value == null || value.isBlank() ? "" : value.trim());
    }

    public String quote(String identifier) {
        return quote + identifier + quote;
    }

    public String qualify(String schema, String table) {
        return schema == null || schema.isBlank()
                ? quote(table)
                : quote(schema) + "." + quote(table);
    }

    public void append(StringBuilder sql, List<String> identifiers) {
        for (int index = 0; index < identifiers.size(); index++) {
            if (index > 0) {
                sql.append(',');
            }
            sql.append(quote(identifiers.get(index)));
        }
    }
}
