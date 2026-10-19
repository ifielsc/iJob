package com.ifielsc.ijob.plugin.batchetl.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfig;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

class JdbcMetadataInspectorTest {
    private final JdbcMetadataInspector inspector = new JdbcMetadataInspector();

    @Test
    void shouldResolveFoldedNamesAndWritableColumns() throws Exception {
        BatchEtlTaskConfig config = config();
        Connection source =
                connection(
                        "APP",
                        "ORDERS",
                        List.of(
                                column("ID", Types.BIGINT, false, false),
                                column("NAME", Types.VARCHAR, true, false)));
        Connection target =
                connection(
                        "DW",
                        "X_ORDERS",
                        List.of(
                                column("ID", Types.NUMERIC, false, false),
                                column("NAME", Types.NVARCHAR, true, false),
                                generated("ROW_VERSION", Types.BIGINT)));

        BatchTablePlan plan = inspector.inspect(source, target, config, "orders", "x_orders");

        assertEquals("APP", plan.sourceSchema());
        assertEquals("ORDERS", plan.sourceTable());
        assertEquals("DW", plan.targetSchema());
        assertEquals("X_ORDERS", plan.targetTable());
        assertEquals(
                List.of("ID", "NAME"),
                plan.columns().stream().map(BatchTablePlan.ColumnMapping::sourceColumn).toList());
        assertEquals("ID", plan.logicalKeys().getFirst().sourceColumn());
    }

    @Test
    void shouldRejectMissingRequiredTargetColumn() throws Exception {
        BatchEtlTaskConfig config = config();
        Connection source =
                connection("APP", "ORDERS", List.of(column("ID", Types.BIGINT, false, false)));
        Connection target =
                connection(
                        "DW",
                        "X_ORDERS",
                        List.of(
                                column("ID", Types.BIGINT, false, false),
                                column("REQUIRED_NAME", Types.VARCHAR, false, false)));

        assertThrows(
                IllegalArgumentException.class,
                () -> inspector.inspect(source, target, config, "orders", "x_orders"));
    }

    @Test
    void shouldRejectIncompatibleColumnFamilies() throws Exception {
        BatchEtlTaskConfig config = config();
        Connection source =
                connection("APP", "ORDERS", List.of(column("ID", Types.BIGINT, false, false)));
        Connection target =
                connection("DW", "X_ORDERS", List.of(column("ID", Types.VARCHAR, false, false)));

        assertThrows(
                IllegalArgumentException.class,
                () -> inspector.inspect(source, target, config, "orders", "x_orders"));
    }

    private BatchEtlTaskConfig config() {
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();
        config.setSourceSchema("APP");
        config.setTargetSchema("DW");
        config.getLogicalKeys().add("id");
        return config;
    }

    private Connection connection(String schema, String table, List<Column> columns) {
        DatabaseMetaData metadata =
                (DatabaseMetaData)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {DatabaseMetaData.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "storesUpperCaseIdentifiers" -> true;
                                            case "storesLowerCaseIdentifiers" -> false;
                                            case "getMaxTableNameLength" -> 128;
                                            case "getColumns" -> rows(schema, table, columns, args);
                                            default -> defaultValue(method.getReturnType());
                                        });
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) ->
                                method.getName().equals("getMetaData")
                                        ? metadata
                                        : defaultValue(method.getReturnType()));
    }

    private CachedRowSet rows(String schema, String table, List<Column> columns, Object[] arguments)
            throws Exception {
        String requestedSchema = (String) arguments[1];
        String requestedTable = (String) arguments[2];
        CachedRowSet rows = emptyRows();
        if (!schema.equals(requestedSchema) || !table.equals(requestedTable)) {
            return rows;
        }
        for (int index = columns.size() - 1; index >= 0; index--) {
            Column column = columns.get(index);
            rows.moveToInsertRow();
            for (int field = 1; field <= 10; field++) {
                rows.updateNull(field);
            }
            rows.updateString("TABLE_SCHEM", schema);
            rows.updateString("TABLE_NAME", table);
            rows.updateString("COLUMN_NAME", column.name());
            rows.updateInt("DATA_TYPE", column.type());
            rows.updateInt(
                    "NULLABLE",
                    column.nullable()
                            ? DatabaseMetaData.columnNullable
                            : DatabaseMetaData.columnNoNulls);
            if (column.hasDefault()) {
                rows.updateString("COLUMN_DEF", "0");
            }
            rows.updateString("IS_AUTOINCREMENT", "NO");
            rows.updateString("IS_GENERATEDCOLUMN", column.generated() ? "YES" : "NO");
            rows.updateInt("ORDINAL_POSITION", index + 1);
            rows.insertRow();
        }
        rows.moveToCurrentRow();
        rows.beforeFirst();
        return rows;
    }

    private CachedRowSet emptyRows() throws Exception {
        List<String> names =
                new ArrayList<>(
                        List.of(
                                "TABLE_CAT",
                                "TABLE_SCHEM",
                                "TABLE_NAME",
                                "COLUMN_NAME",
                                "DATA_TYPE",
                                "NULLABLE",
                                "COLUMN_DEF",
                                "IS_AUTOINCREMENT",
                                "IS_GENERATEDCOLUMN",
                                "ORDINAL_POSITION"));
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(names.size());
        for (int index = 0; index < names.size(); index++) {
            metadata.setColumnName(index + 1, names.get(index));
            metadata.setColumnType(
                    index + 1,
                    names.get(index).equals("DATA_TYPE")
                                    || names.get(index).equals("NULLABLE")
                                    || names.get(index).equals("ORDINAL_POSITION")
                            ? Types.INTEGER
                            : Types.VARCHAR);
        }
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.setMetaData(metadata);
        return rows;
    }

    private Column column(String name, int type, boolean nullable, boolean hasDefault) {
        return new Column(name, type, nullable, hasDefault, false);
    }

    private Column generated(String name, int type) {
        return new Column(name, type, false, false, true);
    }

    private Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private record Column(
            String name, int type, boolean nullable, boolean hasDefault, boolean generated) {}
}
