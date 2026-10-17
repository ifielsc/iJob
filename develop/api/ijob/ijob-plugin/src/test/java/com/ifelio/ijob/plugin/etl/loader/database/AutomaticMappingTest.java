package com.ifelio.ijob.plugin.etl.loader.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifelio.ijob.plugin.etl.loader.TargetMappingPlan;
import com.ifelio.ijob.plugin.etl.loader.TargetMappingPlan.Column;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class AutomaticMappingTest {
    @Test
    void writesRealH2WithOverridesExclusionsGeneratedColumnsAndDefaults() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = ds.getConnection()) {
            c.createStatement()
                    .execute(
                            "CREATE TABLE orders (row_id BIGINT GENERATED ALWAYS AS IDENTITY, id"
                                + " INT NOT NULL, name VARCHAR, total_amount DECIMAL(25,19), secret"
                                + " VARCHAR DEFAULT 'kept', computed INT GENERATED ALWAYS AS (id +"
                                + " 1))");
        }
        var config = config();
        config.getTarget().setTable("ORDERS");
        config.getTarget().getColumns().put("total_amount", "amount");
        config.getTarget().getExcludedFields().add("secret");
        var context = context(config);
        var row = row(1);
        row.put("name", "Alice");
        row.put("amount", new java.math.BigDecimal("0.1234567890123456789"));
        row.put("secret", "never-written");
        row.put("computed", 999);
        row.put("source_only", "unused");
        var loader = new DatabaseLoader(resolver(ds), config.getTarget());
        try {
            loader.open(context);
            loader.writeBatch(List.of(row));
            loader.flush();
        } finally {
            loader.close();
        }
        assertEquals(Map.of("total_amount", "amount"), config.getTarget().getColumns());
        assertEquals(
                List.of("computed", "source_only"),
                context.getTaskContext().getVariable("etl.unmatchedSourceFields"));
        try (Connection c = ds.getConnection();
                var result = c.createStatement().executeQuery("SELECT * FROM orders")) {
            assertTrue(result.next());
            assertEquals("Alice", result.getString("NAME"));
            assertEquals(
                    "0.1234567890123456789", result.getBigDecimal("TOTAL_AMOUNT").toPlainString());
            assertEquals("kept", result.getString("SECRET"));
            assertEquals(2, result.getInt("COMPUTED"));
        }
    }

    @Test
    void validatesLogicalKeysThenUpsertsUsingResolvedCase() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE orders (id INT PRIMARY KEY, name VARCHAR)");
        }
        var config = config();
        config.getTarget().setTable("ORDERS");
        config.getTarget().setMode(EtlTaskConfig.LoadMode.UPSERT);
        config.getTarget().setLogicalKeys(List.of("id"));
        var loader = new DatabaseLoader(resolver(ds), config.getTarget());
        var context = context(config);
        try {
            loader.open(context);
            var row = row(1);
            row.put("name", "before");
            loader.writeBatch(List.of(row));
            row.put("name", "after");
            loader.writeBatch(List.of(row));
            loader.flush();
        } finally {
            loader.close();
        }
        try (var c = ds.getConnection();
                var r = c.createStatement().executeQuery("SELECT name FROM orders")) {
            assertTrue(r.next());
            assertEquals("after", r.getString(1));
            assertFalse(r.next());
        }
    }

    @Test
    void rejectsSchemaDriftAndRollsBackRunTransaction() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var c = ds.getConnection()) {
            c.createStatement().execute("CREATE TABLE orders (id INT)");
        }
        var config = config();
        config.getTarget().setTable("ORDERS");
        var context = context(config);
        var loader = new DatabaseLoader(resolver(ds), config.getTarget());
        try {
            loader.open(context);
            loader.writeBatch(List.of(row(1)));
            var changed = row(2);
            changed.put("extra", 1);
            assertThrows(IllegalStateException.class, () -> loader.writeBatch(List.of(changed)));
        } finally {
            loader.close();
        }
        try (var c = ds.getConnection();
                var r = c.createStatement().executeQuery("SELECT COUNT(*) FROM orders")) {
            assertTrue(r.next());
            assertEquals(0, r.getInt(1));
        }
    }

    @Test
    void checksAllRowsBeforeFirstWriteAndRejectsMissingRequiredTargets() {
        var config = config();
        var context = context(config);
        var changed = row(2);
        changed.put("new", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context,
                                config.getTarget(),
                                List.of(new Column("id", false, true)),
                                List.of(row(1), changed)));
        assertNull(context.getTargetMappingPlan());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context,
                                config.getTarget(),
                                List.of(
                                        new Column("id", false, true),
                                        new Column("required", false, true)),
                                List.of(row(1))));
    }

    @Test
    void rejectsAmbiguousNamesMissingOverridesAndExcludedOverrides() {
        var config = config();
        var row = new DataRecord();
        row.put("ID", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context(config),
                                config.getTarget(),
                                List.of(
                                        new Column("id", false, false),
                                        new Column("Id", false, false)),
                                List.of(row)));
        config.getTarget().getColumns().put("id", "missing");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context(config),
                                config.getTarget(),
                                List.of(new Column("id", false, false)),
                                List.of(row)));
        config.getTarget().getColumns().put("id", "ID");
        config.getTarget().setExcludedFields(List.of("ID"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EtlTaskConfigValidator().validateTarget(config.getTarget()));
    }

    @Test
    void freezesMappingAcrossLoadersInSameFileIncrementalExecution() {
        var config = config();
        var context = context(config);
        var schema = List.of(new Column("id", false, false));
        var first = TargetMappingPlan.resolve(context, config.getTarget(), schema, List.of(row(1)));
        assertSame(
                first,
                TargetMappingPlan.resolve(context, config.getTarget(), schema, List.of(row(2))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context,
                                config.getTarget(),
                                List.of(new Column("id", false, true)),
                                List.of(row(3))));
        var changed = row(3);
        changed.put("later_file", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context, config.getTarget(), schema, List.of(changed)));
    }

    @Test
    void oldJsonRemainsManualAndAutoDoesNotRequireExplicitColumns() throws Exception {
        var target =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(
                                "{\"type\":\"DATABASE\",\"resourceId\":1,\"table\":\"orders\"}",
                                EtlTaskConfig.TargetConfig.class);
        assertEquals(EtlTaskConfig.MappingMode.MANUAL, target.getMappingMode());
        var validator = new EtlTaskConfigValidator();
        assertThrows(IllegalArgumentException.class, () -> validator.validateTarget(target));
        target.setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        assertDoesNotThrow(() -> validator.validateTarget(target));
        target.setMode(EtlTaskConfig.LoadMode.UPSERT);
        assertThrows(IllegalArgumentException.class, () -> validator.validateTarget(target));
        target.setLogicalKeys(List.of("id"));
        assertDoesNotThrow(() -> validator.validateTarget(target));
        var config = config();
        config.setTarget(target);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        TargetMappingPlan.resolve(
                                context(config),
                                target,
                                List.of(new Column("name", false, false)),
                                List.of(row(1))));
    }

    @Test
    void writesRealSqliteMetadata(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws Exception {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("target.db"));
        try (var c = ds.getConnection()) {
            c.createStatement()
                    .execute(
                            "CREATE TABLE orders (id INTEGER NOT NULL, name TEXT DEFAULT"
                                + " 'default-name', doubled INTEGER GENERATED ALWAYS AS (id * 2))");
        }
        var config = config();
        var loader = new DatabaseLoader(resolver(ds), config.getTarget());
        try {
            loader.open(context(config));
            var input = row(7);
            input.put("doubled", 999);
            loader.writeBatch(List.of(input));
            loader.flush();
        } finally {
            loader.close();
        }
        try (var c = ds.getConnection();
                var r = c.createStatement().executeQuery("SELECT id,name,doubled FROM orders")) {
            assertTrue(r.next());
            assertEquals(7, r.getInt(1));
            assertEquals("default-name", r.getString(2));
            assertEquals(14, r.getInt(3));
        }
    }

    @Test
    void keepsScd2ManagedColumnsOutOfAutomaticMapping() throws Exception {
        var ds =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        try (var c = ds.getConnection()) {
            c.createStatement()
                    .execute(
                            "CREATE TABLE orders (id INT, name VARCHAR, valid_from TIMESTAMP NOT"
                                    + " NULL, valid_to TIMESTAMP)");
        }
        var config = config();
        var target = config.getTarget();
        target.setTable("ORDERS");
        target.setMode(EtlTaskConfig.LoadMode.SCD2);
        var scd = new EtlTaskConfig.SCD2Config();
        scd.setBusinessKeys(List.of("id"));
        scd.setCompareFields(List.of("name"));
        scd.setValidFromColumn("valid_from");
        scd.setValidToColumn("valid_to");
        scd.setEffectiveValueField("at");
        target.setScd2(scd);
        var loader = new DatabaseLoader(resolver(ds), target);
        var row = row(1);
        row.put("name", "Alice");
        row.put("at", "2026-09-28T00:00:00");
        try {
            loader.open(context(config));
            loader.writeBatch(List.of(row));
            loader.flush();
        } finally {
            loader.close();
        }
        try (var c = ds.getConnection();
                var r = c.createStatement().executeQuery("SELECT valid_from FROM orders")) {
            assertTrue(r.next());
            assertEquals(
                    java.time.LocalDateTime.of(2026, 9, 28, 0, 0),
                    r.getTimestamp(1).toLocalDateTime());
        }
    }

    private EtlTaskConfig config() {
        var config = new EtlTaskConfig();
        var target = new EtlTaskConfig.TargetConfig();
        config.setTarget(target);
        target.setType(EtlTaskConfig.TargetType.DATABASE);
        target.setResourceId(1L);
        target.setTable("orders");
        target.setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        target.setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        return config;
    }

    private DataRecord row(int id) {
        var row = new DataRecord();
        row.put("id", id);
        return row;
    }

    private ResourceResolver resolver(javax.sql.DataSource ds) {
        return new ResourceResolver(null) {
            @Override
            public javax.sql.DataSource getDataSource(Long id) {
                return ds;
            }
        };
    }

    private EtlContext context(EtlTaskConfig config) {
        return new EtlContext(
                new TaskContext(
                        1L,
                        new ExecutionHandle(1L),
                        null,
                        new RecordingInstanceLogs(),
                        new VariableResolver(),
                        Map.of()) {
                    @Override
                    public void progress(Integer progress) {}
                },
                config);
    }
}
