package com.ifielsc.ijob;

import com.ifielsc.ijob.infra.coordination.id.IdNamespace;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;

/** Checks migration-owned seeds without scanning business tables on startup or allocation. */
final class TableIdSchemaVerifier {
    private TableIdSchemaVerifier() {}

    static void verify(Connection connection) throws SQLException {
        var expected = new HashSet<String>();
        for (IdNamespace namespace : IdNamespace.values()) expected.add(namespace.segmentKey());
        try (var statement =
                        connection.prepareStatement(
                                "SELECT segment_key, next_value FROM ifj_id_segment");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                String key = rows.getString(1);
                if (expected.contains(key)) {
                    long value = rows.getLong(2);
                    if (rows.wasNull() || value < 1 || value == Long.MAX_VALUE) {
                        throw new IllegalStateException(
                                "Table ID namespace is invalid or exhausted: " + key);
                    }
                    expected.remove(key);
                }
            }
        }
        if (!expected.isEmpty())
            throw new IllegalStateException("Table ID migration seeds are missing: " + expected);
        for (String capability :
                new String[] {
                    "per-table-primary-id", "legacy-dag-removed", "parameter-templates"
                }) {
            try (var statement =
                    connection.prepareStatement(
                            "SELECT capability_version, enabled FROM ifj_schema_capability WHERE"
                                    + " capability_key = ?")) {
                statement.setString(1, capability);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) < 1 || !rows.getBoolean(2)) {
                        throw new IllegalStateException(
                                "Required schema capability is missing: " + capability);
                    }
                }
            }
        }
    }
}
