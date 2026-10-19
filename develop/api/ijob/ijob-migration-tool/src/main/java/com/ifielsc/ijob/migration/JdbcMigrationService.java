// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class JdbcMigrationService {

    private static final String MANIFEST = "manifest.json";
    private static final String COMPLETE = "COMPLETE.sha256";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final int BATCH_SIZE = 500;

    private final ObjectMapper mapper;
    private final boolean release;
    private final boolean replica;
    private final List<String> tables;
    private final int logicalVersion;
    private final String formatVersion;

    JdbcMigrationService(ObjectMapper mapper) {
        this(mapper, false, false);
    }

    JdbcMigrationService(ObjectMapper mapper, boolean release, boolean replica) {
        this.mapper = mapper;
        this.release = release;
        this.replica = replica;
        this.tables = release ? ReleaseCatalog.TABLES : SchemaCatalog.TABLES;
        this.logicalVersion =
                release ? ReleaseCatalog.LOGICAL_VERSION : SchemaCatalog.LOGICAL_SCHEMA_VERSION;
        this.formatVersion = release ? ReleaseCatalog.FORMAT : SchemaCatalog.FORMAT_VERSION;
    }

    void preflight(ConnectionSettings settings) throws Exception {
        try (Connection connection = open(settings)) {
            ResolvedSchema schema = resolveSchema(connection, settings.schema());
            validateCurrentSchema(connection, schema, false);
            Map<String, Long> snapshot = coordinationSnapshot(connection, schema);
            printDatabase("PREFLIGHT_OK", connection, schema, snapshot);
        }
    }

    void exportBundle(ConnectionSettings settings, Path bundle) throws Exception {
        if (Files.exists(bundle)) {
            throw new IllegalArgumentException("Bundle path must not already exist: " + bundle);
        }
        Files.createDirectories(bundle.resolve("data"));
        Files.writeString(
                bundle.resolve(INCOMPLETE),
                "export not complete\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        try (Connection connection = open(settings)) {
            configureSnapshot(connection, true);
            ResolvedSchema schema = resolveSchema(connection, settings.schema());
            validateCurrentSchema(connection, schema, false);
            Map<String, Long> snapshot = coordinationSnapshot(connection, schema);
            long active = snapshot.getOrDefault("activeAppRuns", 0L);
            if (active != 0 && !(release && replica)) {
                throw new IllegalStateException(
                        "Source is not offline: "
                                + active
                                + " app run(s) are STARTING/RUNNING/DRAINING");
            }

            List<BundleManifest.Table> tables = new ArrayList<>();
            long maximumId = 0;
            for (String tableName : this.tables) {
                TableRef table = schema.tables().get(tableName);
                TableShape shape = inspectShape(connection, table);
                ExportedTable exported =
                        exportTable(connection, table, shape, bundle.resolve("data"));
                tables.add(exported.manifest());
                maximumId = Math.max(maximumId, exported.maximumNumericPrimaryKey());
                System.out.printf(
                        Locale.ROOT,
                        "exported %-36s %,d row(s)%n",
                        tableName,
                        exported.manifest().rowCount());
            }
            validateIdSegment(connection, schema, maximumId);
            DatabaseMetaData metadata = connection.getMetaData();
            BundleManifest manifest =
                    new BundleManifest(
                            formatVersion,
                            logicalVersion,
                            Instant.now().toString(),
                            new BundleManifest.Source(
                                    metadata.getDatabaseProductName(),
                                    metadata.getDatabaseProductVersion(),
                                    schema.displayName()),
                            snapshot,
                            maximumId,
                            List.copyOf(tables));
            Path manifestPath = bundle.resolve(MANIFEST);
            mapper.writeValue(manifestPath.toFile(), manifest);
            String digest = sha256(Files.readAllBytes(manifestPath));
            Files.writeString(
                    bundle.resolve(COMPLETE),
                    digest + "  " + MANIFEST + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Files.delete(bundle.resolve(INCOMPLETE));
            connection.rollback();
            System.out.printf(
                    Locale.ROOT,
                    "EXPORT_OK tables=%d bundle=%s manifestSha256=%s%n",
                    tables.size(),
                    bundle,
                    digest);
        }
    }

    void importBundle(ConnectionSettings settings, Path bundle) throws Exception {
        BundleManifest manifest = readAndValidateBundle(bundle);
        try (Connection connection = open(settings)) {
            configureSnapshot(connection, false);
            ResolvedSchema schema = resolveSchema(connection, settings.schema());
            validateCurrentSchema(connection, schema, true);
            validateManifestAgainstSchema(connection, schema, manifest);
            if (release
                    && !connection
                            .getMetaData()
                            .getDatabaseProductName()
                            .equals(manifest.source().product())) {
                throw new IllegalStateException(
                        "Release conversion requires the same database product");
            }
            try {
                if (release) {
                    try (var lock = connection.createStatement()) {
                        var base = schema.tables().get("ifj_runtime_guard");
                        var gate =
                                new TableRef(
                                        base.catalog(),
                                        base.schema(),
                                        ReleaseCatalog.vendor(connection).equals("oracle")
                                                ? "IFJ_RELEASE_STATE"
                                                : "ifj_release_state",
                                        quote(connection.getMetaData()),
                                        base.product());
                        if (lock.executeUpdate(
                                        "UPDATE "
                                                + gate.sqlName()
                                                + " SET state='REVIEW_REQUIRED' WHERE"
                                                + " singleton_id=1 AND state='EMPTY'")
                                != 1) {
                            throw new IllegalStateException(
                                    "Target is not an unused release database");
                        }
                    }
                }
                requireEmptyTarget(connection, schema);

                for (String seeded : SchemaCatalog.SEEDED_TABLES) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate(
                                "DELETE FROM " + schema.tables().get(seeded).sqlName());
                    }
                }
                Map<String, BundleManifest.Table> byName = manifestByName(manifest);
                for (String tableName : topologicalOrder(manifest)) {
                    BundleManifest.Table table = byName.get(tableName);
                    importTable(
                            connection,
                            schema.tables().get(tableName),
                            table,
                            bundle.resolve("data").resolve(tableName + ".jsonl"));
                    System.out.printf(
                            Locale.ROOT,
                            "imported %-36s %,d row(s)%n",
                            tableName,
                            table.rowCount());
                }
                validateIdSegment(connection, schema, manifest.maximumNumericPrimaryKey());
                verifyWithinConnection(connection, schema, bundle, manifest);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
            System.out.printf(
                    Locale.ROOT,
                    "IMPORT_OK tables=%d target=%s%n",
                    manifest.tables().size(),
                    schema.displayName());
        }
    }

    void verifyBundle(ConnectionSettings settings, Path bundle) throws Exception {
        BundleManifest manifest = readAndValidateBundle(bundle);
        try (Connection connection = open(settings)) {
            if (release
                    && !connection
                            .getMetaData()
                            .getDatabaseProductName()
                            .equals(manifest.source().product())) {
                throw new IllegalStateException(
                        "Release verification requires the same database product");
            }
            configureSnapshot(connection, true);
            ResolvedSchema schema = resolveSchema(connection, settings.schema());
            validateCurrentSchema(connection, schema, true);
            validateManifestAgainstSchema(connection, schema, manifest);
            verifyWithinConnection(connection, schema, bundle, manifest);
            validateIdSegment(connection, schema, manifest.maximumNumericPrimaryKey());
            connection.rollback();
            System.out.printf(
                    Locale.ROOT,
                    "VERIFY_OK tables=%d target=%s%n",
                    manifest.tables().size(),
                    schema.displayName());
        }
    }

    private Connection open(ConnectionSettings settings) throws Exception {
        if (settings.driver() != null) {
            Class.forName(settings.driver());
        }
        Connection connection =
                DriverManager.getConnection(settings.url(), settings.user(), settings.password());
        if (release
                && connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite")) {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=10000");
            } catch (SQLException exception) {
                connection.close();
                throw exception;
            }
        }
        return connection;
    }

    private void configureSnapshot(Connection connection, boolean readOnly) throws SQLException {
        connection.setAutoCommit(false);
        try {
            // Exports/verifications need a repeatable frozen snapshot. Imports already target an
            // offline empty schema and use one atomic transaction; READ_COMMITTED avoids Oracle 11g
            // ORA-08177 block-cleanout failures after a freshly created baseline.
            connection.setTransactionIsolation(
                    readOnly
                            ? Connection.TRANSACTION_SERIALIZABLE
                            : Connection.TRANSACTION_READ_COMMITTED);
        } catch (SQLException exception) {
            if (!isUnsupported(exception)) {
                throw exception;
            }
        }
        // Xerial requires read-only to be selected before opening the connection. The migration
        // service still issues only SELECT statements on an export/verify SQLite connection.
        if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite")) {
            try {
                connection.setReadOnly(readOnly);
            } catch (SQLException exception) {
                if (!isUnsupported(exception)) {
                    throw exception;
                }
            }
        }
    }

    private static boolean isUnsupported(SQLException exception) {
        return "0A000".equals(exception.getSQLState())
                || exception instanceof java.sql.SQLFeatureNotSupportedException;
    }

    private ResolvedSchema resolveSchema(Connection connection, String configuredSchema)
            throws SQLException {
        if (release
                && configuredSchema == null
                && !connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("SQLite")) {
            throw new IllegalArgumentException(
                    "Release conversion requires an explicit schema/catalog");
        }
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, List<TableRef>> candidates = new LinkedHashMap<>();
        Set<String> expected = new LinkedHashSet<>(tables);
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                String name = lower(tables.getString("TABLE_NAME"));
                if (!expected.contains(name)) {
                    if (release
                            && name.startsWith("ifj_")
                            && !name.equals("ifj_schema_history")
                            && !name.equals("ifj_release_state")
                            && matchesSchema(
                                    configuredSchema,
                                    tables.getString("TABLE_CAT"),
                                    tables.getString("TABLE_SCHEM"))) {
                        throw new IllegalStateException("Unexpected platform table: " + name);
                    }
                    continue;
                }
                String catalog = tables.getString("TABLE_CAT");
                String schema = tables.getString("TABLE_SCHEM");
                if (!matchesSchema(configuredSchema, catalog, schema)) {
                    continue;
                }
                candidates
                        .computeIfAbsent(name, ignored -> new ArrayList<>())
                        .add(
                                new TableRef(
                                        catalog,
                                        schema,
                                        tables.getString("TABLE_NAME"),
                                        quote(metadata),
                                        metadata.getDatabaseProductName()));
            }
        }
        Map<String, TableRef> resolved = new LinkedHashMap<>();
        for (String name : this.tables) {
            List<TableRef> matches = candidates.getOrDefault(name, List.of());
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "Expected exactly one "
                                + name
                                + " in schema "
                                + Objects.toString(configuredSchema, "<connection-default>")
                                + ", found "
                                + matches.size());
            }
            resolved.put(name, matches.get(0));
        }
        String display = configuredSchema;
        if (display == null) {
            TableRef first = resolved.values().iterator().next();
            display =
                    first.schema() != null
                            ? first.schema()
                            : Objects.toString(first.catalog(), "<default>");
        }
        return new ResolvedSchema(display, Map.copyOf(resolved));
    }

    private static boolean matchesSchema(String configured, String catalog, String schema) {
        if (configured == null) {
            return true;
        }
        return configured.equalsIgnoreCase(Objects.toString(schema, ""))
                || configured.equalsIgnoreCase(Objects.toString(catalog, ""));
    }

    private static String quote(DatabaseMetaData metadata) throws SQLException {
        String value = metadata.getIdentifierQuoteString();
        return value == null || value.isBlank() ? "\"" : value.trim();
    }

    private void validateCurrentSchema(Connection connection, ResolvedSchema schema, boolean target)
            throws SQLException {
        TableRef capability = schema.tables().get("ifj_schema_capability");
        validateMigrationVersion(connection, capability, target);
        String sql =
                "SELECT capability_version, enabled FROM "
                        + capability.sqlName()
                        + " WHERE capability_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "platform-schema");
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()
                        || rows.getInt(1) != logicalVersion
                        || !rows.getBoolean(2)
                        || rows.next()) {
                    throw new IllegalStateException(
                            "Database does not have the required logical schema capability");
                }
            }
        }
    }

    private void validateMigrationVersion(
            Connection connection, TableRef capability, boolean target) throws SQLException {
        // Later additive migrations retain the logical capability number. Checking that number
        // alone can silently export a partial catalog (for example, omitting V30 file records).
        DatabaseMetaData metadata = connection.getMetaData();
        TableRef history = null;
        try (ResultSet tables =
                metadata.getTables(
                        capability.catalog(), capability.schema(), "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                if ("ifj_schema_history".equals(lower(tables.getString("TABLE_NAME")))) {
                    history =
                            new TableRef(
                                    tables.getString("TABLE_CAT"),
                                    tables.getString("TABLE_SCHEM"),
                                    tables.getString("TABLE_NAME"),
                                    quote(metadata),
                                    metadata.getDatabaseProductName());
                    break;
                }
            }
        }
        if (history == null) throw new IllegalStateException("Migration history is required");
        if (release) {
            ReleaseCatalog.verifyHistory(connection, history.sqlName(), target);
            return;
        }
        String versionColumn = null;
        try (ResultSet columns =
                metadata.getColumns(history.catalog(), history.schema(), history.name(), "%")) {
            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                if ("version".equals(lower(name)))
                    versionColumn = quote(metadata) + name + quote(metadata);
            }
        }
        if (versionColumn == null)
            throw new IllegalStateException("Migration history has no version column");
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT " + versionColumn + " FROM " + history.sqlName())) {
            while (rows.next()) {
                String version = rows.getString(1);
                if (version != null
                        && new java.math.BigDecimal(version)
                                        .compareTo(java.math.BigDecimal.valueOf(logicalVersion))
                                > 0) {
                    throw new IllegalStateException(
                            "Offline V27 migration does not support later migrations; use a"
                                    + " compatible migration tool");
                }
            }
        }
    }

    private Map<String, Long> coordinationSnapshot(Connection connection, ResolvedSchema schema)
            throws SQLException {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put(
                "activeAppRuns",
                countWhere(
                        connection,
                        schema,
                        "ifj_app_run",
                        "status IN ('STARTING','RUNNING','DRAINING')"));
        result.put(
                "activeInstances",
                countWhere(
                        connection,
                        schema,
                        "ifj_instance",
                        "status IN ('WAITING','RUNNING','PAUSED','WAITING_EVENT')"));
        result.put(
                "activeWorkItems",
                countWhere(
                        connection,
                        schema,
                        "ifj_trigger_work_item",
                        "status IN ('READY','RETRY_WAIT','CLAIMED','SUSPENDED')"));
        result.put(
                "activeOutbox",
                countWhere(
                        connection,
                        schema,
                        "ifj_notification_outbox",
                        "status IN ('READY','RETRY_WAIT','CLAIMED')"));
        result.put(
                "processingCommands",
                countWhere(connection, schema, "ifj_runtime_command", "status = 'PROCESSING'"));
        return Map.copyOf(result);
    }

    private long countWhere(
            Connection connection, ResolvedSchema schema, String table, String predicate)
            throws SQLException {
        String sql =
                "SELECT COUNT(*) FROM "
                        + schema.tables().get(table).sqlName()
                        + " WHERE "
                        + predicate;
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private TableShape inspectShape(Connection connection, TableRef table) throws SQLException {
        List<ColumnMeta> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT * FROM " + table.sqlName() + " WHERE 1 = 0")) {
            ResultSetMetaData metadata = rows.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String name =
                        SchemaCatalog.logicalColumn(
                                table.product(),
                                table.logicalName(),
                                lower(metadata.getColumnName(index)));
                columns.add(
                        new ColumnMeta(
                                name,
                                metadata.getColumnType(index),
                                metadata.isNullable(index) != ResultSetMetaData.columnNoNulls,
                                logicalType(
                                        table.logicalName(), name, metadata.getColumnType(index))));
            }
        }
        List<KeyColumn> primaryKeys = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet keys =
                metadata.getPrimaryKeys(table.catalog(), table.schema(), table.name())) {
            while (keys.next()) {
                primaryKeys.add(
                        new KeyColumn(
                                keys.getShort("KEY_SEQ"),
                                SchemaCatalog.logicalColumn(
                                        table.product(),
                                        table.logicalName(),
                                        lower(keys.getString("COLUMN_NAME")))));
            }
        }
        primaryKeys.sort(Comparator.comparingInt(KeyColumn::sequence));
        if (primaryKeys.isEmpty()) {
            throw new IllegalStateException("Table has no primary key: " + table.logicalName());
        }
        List<BundleManifest.ForeignKey> foreignKeys = inspectForeignKeys(connection, table);
        return new TableShape(
                List.copyOf(columns),
                primaryKeys.stream().map(KeyColumn::name).toList(),
                foreignKeys);
    }

    private List<BundleManifest.ForeignKey> inspectForeignKeys(
            Connection connection, TableRef table) throws SQLException {
        record MutableForeignKey(
                Map<Short, String> columns, String target, Map<Short, String> targetColumns) {}
        Map<String, MutableForeignKey> keys = new LinkedHashMap<>();
        try (ResultSet rows =
                connection
                        .getMetaData()
                        .getImportedKeys(table.catalog(), table.schema(), table.name())) {
            while (rows.next()) {
                String keyName = Objects.toString(rows.getString("FK_NAME"), "fk-" + rows.getRow());
                short sequence = rows.getShort("KEY_SEQ");
                MutableForeignKey key =
                        keys.computeIfAbsent(
                                keyName,
                                ignored ->
                                        new MutableForeignKey(
                                                new LinkedHashMap<>(),
                                                lower(rowsUnchecked(rows, "PKTABLE_NAME")),
                                                new LinkedHashMap<>()));
                key.columns()
                        .put(
                                sequence,
                                SchemaCatalog.logicalColumn(
                                        table.product(),
                                        table.logicalName(),
                                        lower(rows.getString("FKCOLUMN_NAME"))));
                key.targetColumns()
                        .put(
                                sequence,
                                SchemaCatalog.logicalColumn(
                                        table.product(),
                                        key.target(),
                                        lower(rows.getString("PKCOLUMN_NAME"))));
            }
        }
        List<BundleManifest.ForeignKey> result = new ArrayList<>();
        for (MutableForeignKey key : keys.values()) {
            result.add(
                    new BundleManifest.ForeignKey(
                            List.copyOf(key.columns().values()),
                            key.target(),
                            List.copyOf(key.targetColumns().values())));
        }
        return List.copyOf(result);
    }

    private static String rowsUnchecked(ResultSet rows, String column) {
        try {
            return rows.getString(column);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BundleManifest.LogicalType logicalType(
            String table, String column, int jdbcType) {
        if (SchemaCatalog.isBoolean(table, column)) {
            return BundleManifest.LogicalType.BOOLEAN;
        }
        if (SchemaCatalog.isTimestamp(column)) {
            return BundleManifest.LogicalType.TIMESTAMP;
        }
        if (SchemaCatalog.isDate(table, column)) {
            return BundleManifest.LogicalType.DATE;
        }
        return switch (jdbcType) {
            case Types.TINYINT,
                            Types.SMALLINT,
                            Types.INTEGER,
                            Types.BIGINT,
                            Types.NUMERIC,
                            Types.DECIMAL,
                            Types.FLOAT,
                            Types.REAL,
                            Types.DOUBLE ->
                    BundleManifest.LogicalType.NUMBER;
            case Types.BIT, Types.BOOLEAN -> BundleManifest.LogicalType.BOOLEAN;
            case Types.DATE -> BundleManifest.LogicalType.DATE;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> BundleManifest.LogicalType.TIME;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                    BundleManifest.LogicalType.TIMESTAMP;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB ->
                    BundleManifest.LogicalType.BINARY;
            default -> BundleManifest.LogicalType.TEXT;
        };
    }

    private ExportedTable exportTable(
            Connection connection, TableRef table, TableShape shape, Path dataDirectory)
            throws Exception {
        Path output = dataDirectory.resolve(table.logicalName() + ".jsonl");
        MessageDigest digest = messageDigest();
        long rowCount = 0;
        long normalizedEmptyStrings = 0;
        long maximumPk = 0;
        String orderBy =
                shape.primaryKey().stream()
                        .map(table::quoted)
                        .collect(java.util.stream.Collectors.joining(", "));
        String sql = "SELECT * FROM " + table.sqlName() + " ORDER BY " + orderBy;
        try (Statement statement = connection.createStatement()) {
            statement.setFetchSize(500);
            try (ResultSet rows = statement.executeQuery(sql);
                    BufferedWriter writer =
                            Files.newBufferedWriter(
                                    output,
                                    StandardCharsets.UTF_8,
                                    StandardOpenOption.CREATE_NEW)) {
                Map<String, Integer> indexes = columnIndexes(rows.getMetaData(), table);
                while (rows.next()) {
                    ArrayNode row = mapper.createArrayNode();
                    for (ColumnMeta column : shape.columns()) {
                        JsonNode value =
                                readValue(
                                        rows,
                                        indexes.get(column.name()),
                                        column.type(),
                                        column.jdbcType());
                        if (column.type() == BundleManifest.LogicalType.TEXT && value.isNull()) {
                            String raw = rows.getString(indexes.get(column.name()));
                            if (raw != null && raw.isEmpty()) {
                                if (!column.nullable()) {
                                    throw new IllegalStateException(
                                            "Non-nullable empty text cannot be represented "
                                                    + "portably on Oracle: "
                                                    + table.logicalName()
                                                    + "."
                                                    + column.name());
                                }
                                normalizedEmptyStrings++;
                            }
                        }
                        row.add(value);
                    }
                    maximumPk = Math.max(maximumPk, numericPrimaryKeyMaximum(row, shape));
                    String json =
                            mapper.writer()
                                    .without(
                                            com.fasterxml.jackson.databind.SerializationFeature
                                                    .INDENT_OUTPUT)
                                    .writeValueAsString(row);
                    writer.write(json);
                    // Bundle bytes are platform-independent; never use the host line separator.
                    writer.write('\n');
                    digest.update(CanonicalCodec.lineBytes(json));
                    rowCount++;
                }
            }
        }
        List<BundleManifest.Column> columns =
                shape.columns().stream()
                        .map(
                                column ->
                                        new BundleManifest.Column(
                                                column.name(), column.type(), column.nullable()))
                        .toList();
        BundleManifest.Table manifest =
                new BundleManifest.Table(
                        table.logicalName(),
                        columns,
                        shape.primaryKey(),
                        shape.foreignKeys(),
                        rowCount,
                        hex(digest.digest()),
                        normalizedEmptyStrings);
        return new ExportedTable(manifest, maximumPk);
    }

    private static Map<String, Integer> columnIndexes(ResultSetMetaData metadata, TableRef table)
            throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String physical = lower(metadata.getColumnName(index));
            result.put(
                    SchemaCatalog.logicalColumn(table.product(), table.logicalName(), physical),
                    index);
        }
        return result;
    }

    private static long numericPrimaryKeyMaximum(ArrayNode row, TableShape shape) {
        long maximum = 0;
        for (String primaryKey : shape.primaryKey()) {
            int index = indexOfColumn(shape.columns(), primaryKey);
            if (shape.columns().get(index).type() != BundleManifest.LogicalType.NUMBER
                    || row.get(index).isNull()) {
                continue;
            }
            try {
                maximum =
                        Math.max(
                                maximum,
                                new BigDecimal(row.get(index).textValue()).longValueExact());
            } catch (ArithmeticException ignored) {
                // Non-integral or wider numeric primary keys are not part of the platform ID
                // segment.
            }
        }
        return maximum;
    }

    private static int indexOfColumn(List<ColumnMeta> columns, String name) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalStateException("Missing column " + name);
    }

    private BundleManifest readAndValidateBundle(Path bundle) throws Exception {
        Path manifestPath = bundle.resolve(MANIFEST);
        Path completePath = bundle.resolve(COMPLETE);
        if (!Files.isRegularFile(manifestPath)
                || !Files.isRegularFile(completePath)
                || Files.exists(bundle.resolve(INCOMPLETE))) {
            throw new IllegalArgumentException("Bundle is incomplete: " + bundle);
        }
        String marker = Files.readString(completePath, StandardCharsets.UTF_8).trim();
        String expected = marker.split("\\s+", 2)[0];
        String actual = sha256(Files.readAllBytes(manifestPath));
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Manifest checksum mismatch");
        }
        BundleManifest manifest = mapper.readValue(manifestPath.toFile(), BundleManifest.class);
        if (!formatVersion.equals(manifest.formatVersion())
                || manifest.logicalSchemaVersion() != logicalVersion) {
            throw new IllegalStateException("Unsupported bundle format or logical schema version");
        }
        if (!manifest.tables().stream().map(BundleManifest.Table::name).toList().equals(tables)) {
            throw new IllegalStateException(
                    "Bundle table catalog does not match the selected catalog");
        }
        for (BundleManifest.Table table : manifest.tables()) {
            Path data = bundle.resolve("data").resolve(table.name() + ".jsonl");
            if (!Files.isRegularFile(data) || !sha256(data).equalsIgnoreCase(table.sha256())) {
                throw new IllegalStateException("Data checksum mismatch: " + table.name());
            }
        }
        return manifest;
    }

    private void validateManifestAgainstSchema(
            Connection connection, ResolvedSchema schema, BundleManifest manifest)
            throws SQLException {
        for (BundleManifest.Table table : manifest.tables()) {
            TableShape shape = inspectShape(connection, schema.tables().get(table.name()));
            Map<String, ColumnMeta> actualColumns = new LinkedHashMap<>();
            for (ColumnMeta column : shape.columns()) {
                actualColumns.put(column.name(), column);
            }
            List<String> expectedColumns =
                    table.columns().stream().map(BundleManifest.Column::name).toList();
            if (actualColumns.size() != expectedColumns.size()
                    || !actualColumns.keySet().equals(new HashSet<>(expectedColumns))
                    || !shape.primaryKey().equals(table.primaryKey())) {
                throw new IllegalStateException("Target shape mismatch: " + table.name());
            }
            // Frozen vendor migrations may add/rebuild columns in different physical orders.
            // Import bindings and digest verification already follow manifest column names.
            for (BundleManifest.Column expected : table.columns()) {
                ColumnMeta actual = actualColumns.get(expected.name());
                // Oracle deliberately leaves selected CLOB columns nullable because empty
                // VARCHAR/CLOB
                // values collapse to NULL. Logical type identity is portable; physical nullability
                // is
                // enforced by the target baseline and by the actual imported values.
                if (actual.type() != expected.type()) {
                    throw new IllegalStateException(
                            "Target logical column mismatch: "
                                    + table.name()
                                    + "."
                                    + expected.name());
                }
            }
        }
    }

    private void requireEmptyTarget(Connection connection, ResolvedSchema schema)
            throws SQLException {
        for (String tableName : tables) {
            long count;
            try (Statement statement = connection.createStatement();
                    ResultSet rows =
                            statement.executeQuery(
                                    "SELECT COUNT(*) FROM "
                                            + schema.tables().get(tableName).sqlName())) {
                rows.next();
                count = rows.getLong(1);
            }
            long allowed =
                    switch (tableName) {
                        case "ifj_id_segment" -> release ? 29 : 1;
                        case "ifj_schema_capability" -> release ? 12 : 9;
                        case "ifj_runtime_guard" -> 3;
                        default -> 0;
                    };
            if (count != allowed) {
                throw new IllegalStateException(
                        "Target is not an untouched baseline: "
                                + tableName
                                + " has "
                                + count
                                + " row(s), expected "
                                + allowed);
            }
            if (release && SchemaCatalog.SEEDED_TABLES.contains(tableName)) {
                ReleaseCatalog.verifyEmptySeeds(
                        connection, tableName, schema.tables().get(tableName).sqlName());
            }
            if (!release && tableName.equals("ifj_runtime_guard")) {
                try (Statement statement = connection.createStatement();
                        ResultSet rows =
                                statement.executeQuery(
                                        "SELECT COUNT(*) FROM "
                                                + schema.tables().get(tableName).sqlName()
                                                + " WHERE guard_key IN"
                                                + " ('concurrency-policy','runtime-lifecycle','auth-accounts')"
                                                + " AND revision=0")) {
                    rows.next();
                    if (rows.getLong(1) != 3) {
                        throw new IllegalStateException(
                                "Target runtime guards are not untouched V27 seeds");
                    }
                }
            }
        }
    }

    private void importTable(
            Connection connection, TableRef target, BundleManifest.Table table, Path data)
            throws Exception {
        TableShape targetShape = inspectShape(connection, target);
        Map<String, ColumnMeta> targetColumns = new LinkedHashMap<>();
        for (ColumnMeta column : targetShape.columns()) {
            targetColumns.put(column.name(), column);
        }
        Set<String> selfForeignKeyColumns = new HashSet<>();
        for (BundleManifest.ForeignKey foreignKey : table.foreignKeys()) {
            if (table.name().equals(foreignKey.referencedTable())) {
                selfForeignKeyColumns.addAll(foreignKey.columns());
            }
        }
        String columnSql =
                table.columns().stream()
                        .map(column -> target.quoted(column.name()))
                        .collect(java.util.stream.Collectors.joining(", "));
        String placeholders =
                table.columns().stream()
                        .map(ignored -> "?")
                        .collect(java.util.stream.Collectors.joining(", "));
        String insertSql =
                "INSERT INTO "
                        + target.sqlName()
                        + " ("
                        + columnSql
                        + ") VALUES ("
                        + placeholders
                        + ")";
        if (release && !selfForeignKeyColumns.isEmpty()) {
            importSelfReferencingTable(connection, table, data, targetColumns, insertSql);
            return;
        }
        long count = 0;
        try (PreparedStatement statement = connection.prepareStatement(insertSql);
                BufferedReader reader = Files.newBufferedReader(data, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                ArrayNode row = requireArray(line, table);
                for (int index = 0; index < table.columns().size(); index++) {
                    BundleManifest.Column column = table.columns().get(index);
                    JsonNode value =
                            selfForeignKeyColumns.contains(column.name()) ? null : row.get(index);
                    bindValue(
                            statement,
                            index + 1,
                            value,
                            column.type(),
                            targetColumns.get(column.name()).jdbcType());
                }
                statement.addBatch();
                count++;
                if (count % BATCH_SIZE == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        if (count != table.rowCount()) {
            throw new IllegalStateException(
                    "Bundle row count mismatch while importing " + table.name());
        }
        if (!selfForeignKeyColumns.isEmpty()) {
            restoreSelfForeignKeys(
                    connection, target, table, data, targetColumns, selfForeignKeyColumns);
        }
    }

    private void restoreSelfForeignKeys(
            Connection connection,
            TableRef target,
            BundleManifest.Table table,
            Path data,
            Map<String, ColumnMeta> targetColumns,
            Set<String> selfColumns)
            throws Exception {
        List<String> columns =
                table.columns().stream()
                        .map(BundleManifest.Column::name)
                        .filter(selfColumns::contains)
                        .toList();
        String assignments =
                columns.stream()
                        .map(name -> target.quoted(name) + " = ?")
                        .collect(java.util.stream.Collectors.joining(", "));
        String predicate =
                table.primaryKey().stream()
                        .map(name -> target.quoted(name) + " = ?")
                        .collect(java.util.stream.Collectors.joining(" AND "));
        String sql = "UPDATE " + target.sqlName() + " SET " + assignments + " WHERE " + predicate;
        try (PreparedStatement statement = connection.prepareStatement(sql);
                BufferedReader reader = Files.newBufferedReader(data, StandardCharsets.UTF_8)) {
            String line;
            int batched = 0;
            while ((line = reader.readLine()) != null) {
                ArrayNode row = requireArray(line, table);
                boolean anyValue = false;
                int parameter = 1;
                for (String name : columns) {
                    int index = manifestColumnIndex(table, name);
                    JsonNode value = row.get(index);
                    anyValue |= !value.isNull();
                    BundleManifest.Column column = table.columns().get(index);
                    bindValue(
                            statement,
                            parameter++,
                            value,
                            column.type(),
                            targetColumns.get(name).jdbcType());
                }
                for (String name : table.primaryKey()) {
                    int index = manifestColumnIndex(table, name);
                    BundleManifest.Column column = table.columns().get(index);
                    bindValue(
                            statement,
                            parameter++,
                            row.get(index),
                            column.type(),
                            targetColumns.get(name).jdbcType());
                }
                if (anyValue) {
                    statement.addBatch();
                    batched++;
                    if (batched % BATCH_SIZE == 0) {
                        statement.executeBatch();
                    }
                }
            }
            statement.executeBatch();
        }
    }

    private void importSelfReferencingTable(
            Connection connection,
            BundleManifest.Table table,
            Path data,
            Map<String, ColumnMeta> targetColumns,
            String insertSql)
            throws Exception {
        var selfKeys =
                table.foreignKeys().stream()
                        .filter(key -> table.name().equals(key.referencedTable()))
                        .toList();
        for (var key : selfKeys) {
            if (!key.referencedColumns().equals(table.primaryKey())) {
                throw new IllegalStateException(
                        "Self reference must target the primary key: " + table.name());
            }
        }
        Set<List<JsonNode>> inserted = new HashSet<>();
        // Insert parents first: restoring links with UPDATE would fire timestamp/audit triggers.
        while (inserted.size() < table.rowCount()) {
            int before = inserted.size();
            try (var statement = connection.prepareStatement(insertSql);
                    var reader = Files.newBufferedReader(data, StandardCharsets.UTF_8)) {
                String line;
                int batched = 0;
                while ((line = reader.readLine()) != null) {
                    var row = requireArray(line, table);
                    var primary = keyValues(row, table, table.primaryKey());
                    if (inserted.contains(primary)) continue;
                    boolean ready = true;
                    for (var key : selfKeys) {
                        var reference = keyValues(row, table, key.columns());
                        if (reference.stream().noneMatch(JsonNode::isNull)
                                && !reference.equals(primary)
                                && !inserted.contains(reference)) ready = false;
                    }
                    if (!ready) continue;
                    for (int index = 0; index < table.columns().size(); index++) {
                        var column = table.columns().get(index);
                        bindValue(
                                statement,
                                index + 1,
                                row.get(index),
                                column.type(),
                                targetColumns.get(column.name()).jdbcType());
                    }
                    statement.addBatch();
                    inserted.add(primary);
                    if (++batched % BATCH_SIZE == 0) statement.executeBatch();
                }
                statement.executeBatch();
            }
            if (before == inserted.size())
                throw new IllegalStateException(
                        "Cyclic or missing self references: " + table.name());
        }
    }

    private List<JsonNode> keyValues(
            ArrayNode row, BundleManifest.Table table, List<String> columns) {
        return columns.stream().map(column -> row.get(manifestColumnIndex(table, column))).toList();
    }

    private ArrayNode requireArray(String line, BundleManifest.Table table) throws IOException {
        JsonNode parsed = mapper.readTree(line);
        if (!(parsed instanceof ArrayNode array) || array.size() != table.columns().size()) {
            throw new IllegalStateException("Invalid row encoding in " + table.name());
        }
        return array;
    }

    private static int manifestColumnIndex(BundleManifest.Table table, String name) {
        for (int index = 0; index < table.columns().size(); index++) {
            if (table.columns().get(index).name().equals(name)) {
                return index;
            }
        }
        throw new IllegalStateException("Manifest column not found: " + table.name() + "." + name);
    }

    private JsonNode readValue(
            ResultSet rows, int index, BundleManifest.LogicalType type, int jdbcType)
            throws SQLException {
        if (release && (type == BundleManifest.LogicalType.TEXT || isTextType(jdbcType))) {
            String value = rows.getString(index);
            return value == null
                    ? com.fasterxml.jackson.databind.node.NullNode.instance
                    : com.fasterxml.jackson.databind.node.TextNode.valueOf(value);
        }
        if (release && type == BundleManifest.LogicalType.TIMESTAMP) {
            var value = rows.getTimestamp(index, CanonicalCodec.utcCalendar());
            return value == null
                    ? com.fasterxml.jackson.databind.node.NullNode.instance
                    : com.fasterxml.jackson.databind.node.TextNode.valueOf(
                            LocalDateTime.ofInstant(value.toInstant(), java.time.ZoneOffset.UTC)
                                    .toString());
        }
        return CanonicalCodec.read(rows, index, type, jdbcType);
    }

    private void bindValue(
            PreparedStatement statement,
            int index,
            JsonNode node,
            BundleManifest.LogicalType type,
            int jdbcType)
            throws SQLException {
        if (release
                && ((type == BundleManifest.LogicalType.TIMESTAMP && isTextType(jdbcType))
                        || (type == BundleManifest.LogicalType.TEXT
                                && jdbcType == Types.LONGVARCHAR))) {
            statement.setString(index, CanonicalCodec.value(node));
        } else {
            bind(statement, index, node, type, jdbcType);
        }
    }

    private static void bind(
            PreparedStatement statement,
            int index,
            JsonNode node,
            BundleManifest.LogicalType type,
            int jdbcType)
            throws SQLException {
        String value = CanonicalCodec.value(node);
        if (value == null) {
            statement.setNull(index, jdbcType);
            return;
        }
        switch (type) {
            case TEXT -> {
                if (jdbcType == Types.NVARCHAR || jdbcType == Types.NCHAR) {
                    statement.setNString(index, value);
                } else if (jdbcType == Types.NCLOB || jdbcType == Types.LONGNVARCHAR) {
                    statement.setNCharacterStream(
                            index, new java.io.StringReader(value), value.length());
                } else if (jdbcType == Types.CLOB || jdbcType == Types.LONGVARCHAR) {
                    statement.setCharacterStream(
                            index, new java.io.StringReader(value), value.length());
                } else {
                    statement.setString(index, value);
                }
            }
            case NUMBER -> statement.setBigDecimal(index, new BigDecimal(value));
            case BOOLEAN -> statement.setBoolean(index, Boolean.parseBoolean(value));
            case TIMESTAMP -> {
                if (isTextType(jdbcType)) {
                    statement.setString(index, value.replace('T', ' '));
                } else {
                    statement.setTimestamp(
                            index,
                            java.sql.Timestamp.from(
                                    LocalDateTime.parse(value).toInstant(java.time.ZoneOffset.UTC)),
                            CanonicalCodec.utcCalendar());
                }
            }
            case DATE -> {
                if (isTextType(jdbcType)) {
                    statement.setString(index, value);
                } else {
                    statement.setDate(
                            index,
                            new java.sql.Date(
                                    LocalDate.parse(value)
                                            .atStartOfDay()
                                            .toInstant(java.time.ZoneOffset.UTC)
                                            .toEpochMilli()),
                            CanonicalCodec.utcCalendar());
                }
            }
            case TIME -> {
                if (isTextType(jdbcType)) {
                    statement.setString(index, value);
                } else {
                    statement.setTime(
                            index,
                            new java.sql.Time(
                                    LocalTime.parse(value)
                                            .atDate(LocalDate.of(1970, 1, 1))
                                            .toInstant(java.time.ZoneOffset.UTC)
                                            .toEpochMilli()),
                            CanonicalCodec.utcCalendar());
                }
            }
            case BINARY -> statement.setBytes(index, Base64.getDecoder().decode(value));
        }
    }

    private static boolean isTextType(int jdbcType) {
        return jdbcType == Types.CHAR
                || jdbcType == Types.VARCHAR
                || jdbcType == Types.LONGVARCHAR
                || jdbcType == Types.NCHAR
                || jdbcType == Types.NVARCHAR
                || jdbcType == Types.LONGNVARCHAR
                || jdbcType == Types.CLOB
                || jdbcType == Types.NCLOB;
    }

    private List<String> topologicalOrder(BundleManifest manifest) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        Set<String> all = new LinkedHashSet<>(tables);
        for (BundleManifest.Table table : manifest.tables()) {
            Set<String> tableDependencies = new LinkedHashSet<>();
            for (BundleManifest.ForeignKey key : table.foreignKeys()) {
                if (all.contains(key.referencedTable())
                        && !table.name().equals(key.referencedTable())) {
                    tableDependencies.add(key.referencedTable());
                }
            }
            dependencies.put(table.name(), tableDependencies);
        }
        List<String> result = new ArrayList<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (String table : tables) {
            if (dependencies.get(table).isEmpty()) {
                ready.add(table);
            }
        }
        while (!ready.isEmpty()) {
            String table = ready.removeFirst();
            if (result.contains(table)) {
                continue;
            }
            result.add(table);
            for (String candidate : tables) {
                Set<String> required = dependencies.get(candidate);
                required.remove(table);
                if (required.isEmpty() && !result.contains(candidate)) {
                    ready.addLast(candidate);
                }
            }
        }
        if (result.size() != tables.size()) {
            Set<String> cyclic = new LinkedHashSet<>(tables);
            cyclic.removeAll(result);
            throw new IllegalStateException(
                    "Cross-table foreign-key cycle is not supported: " + cyclic);
        }
        return result;
    }

    private void verifyWithinConnection(
            Connection connection, ResolvedSchema schema, Path bundle, BundleManifest manifest)
            throws Exception {
        for (BundleManifest.Table table : manifest.tables()) {
            TableRef target = schema.tables().get(table.name());
            TableShape shape = inspectShape(connection, target);
            DigestResult digest = digestTable(connection, target, shape, table);
            if (digest.rowCount() != table.rowCount()
                    || !digest.sha256().equalsIgnoreCase(table.sha256())) {
                String difference =
                        firstDifference(
                                connection,
                                target,
                                table,
                                bundle.resolve("data").resolve(table.name() + ".jsonl"));
                throw new IllegalStateException(
                        "Verification mismatch for "
                                + table.name()
                                + ": expected rows="
                                + table.rowCount()
                                + " sha256="
                                + table.sha256()
                                + ", actual rows="
                                + digest.rowCount()
                                + " sha256="
                                + digest.sha256()
                                + ", "
                                + difference);
            }
            System.out.printf(
                    Locale.ROOT, "verified %-36s %,d row(s)%n", table.name(), table.rowCount());
        }
    }

    private String firstDifference(
            Connection connection, TableRef table, BundleManifest.Table manifest, Path expectedFile)
            throws Exception {
        String orderBy =
                manifest.primaryKey().stream()
                        .map(table::quoted)
                        .collect(java.util.stream.Collectors.joining(", "));
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT * FROM " + table.sqlName() + " ORDER BY " + orderBy);
                BufferedReader expected =
                        Files.newBufferedReader(expectedFile, StandardCharsets.UTF_8)) {
            Map<String, Integer> indexes = columnIndexes(rows.getMetaData(), table);
            long rowNumber = 0;
            while (true) {
                boolean hasActual = rows.next();
                String line = expected.readLine();
                if (!hasActual || line == null) {
                    return "first difference at row " + (rowNumber + 1) + " (row presence differs)";
                }
                rowNumber++;
                ArrayNode expectedRow = requireArray(line, manifest);
                List<String> columns = new ArrayList<>();
                for (int index = 0; index < manifest.columns().size(); index++) {
                    BundleManifest.Column column = manifest.columns().get(index);
                    int resultIndex = indexes.get(column.name());
                    JsonNode actual =
                            readValue(
                                    rows,
                                    resultIndex,
                                    column.type(),
                                    rows.getMetaData().getColumnType(resultIndex));
                    if (!actual.equals(expectedRow.get(index))) {
                        columns.add(column.name());
                    }
                }
                if (!columns.isEmpty()) {
                    return "first difference at row " + rowNumber + " column(s) " + columns;
                }
                if (!rows.isLast() && expected.ready()) {
                    continue;
                }
                if (rows.isLast() && expected.readLine() == null) {
                    return "no logical row difference; byte encoding differs";
                }
            }
        }
    }

    private DigestResult digestTable(
            Connection connection, TableRef table, TableShape shape, BundleManifest.Table manifest)
            throws Exception {
        MessageDigest digest = messageDigest();
        long count = 0;
        String orderBy =
                manifest.primaryKey().stream()
                        .map(table::quoted)
                        .collect(java.util.stream.Collectors.joining(", "));
        try (Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT * FROM " + table.sqlName() + " ORDER BY " + orderBy)) {
            Map<String, Integer> indexes = columnIndexes(rows.getMetaData(), table);
            while (rows.next()) {
                ArrayNode row = mapper.createArrayNode();
                for (BundleManifest.Column column : manifest.columns()) {
                    int resultIndex = indexes.get(column.name());
                    row.add(
                            readValue(
                                    rows,
                                    resultIndex,
                                    column.type(),
                                    rows.getMetaData().getColumnType(resultIndex)));
                }
                String json =
                        mapper.writer()
                                .without(
                                        com.fasterxml.jackson.databind.SerializationFeature
                                                .INDENT_OUTPUT)
                                .writeValueAsString(row);
                digest.update(CanonicalCodec.lineBytes(json));
                count++;
            }
        }
        return new DigestResult(count, hex(digest.digest()));
    }

    private void validateIdSegment(Connection connection, ResolvedSchema schema, long maximumId)
            throws SQLException {
        if (release) {
            ReleaseCatalog.verifyIdSegments(
                    connection, name -> schema.tables().get(name).sqlName());
            return;
        }
        String sql =
                "SELECT next_value FROM "
                        + schema.tables().get("ifj_id_segment").sqlName()
                        + " WHERE segment_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "platform-primary-id");
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getLong(1) <= maximumId || rows.next()) {
                    throw new IllegalStateException(
                            "platform-primary-id next_value must be greater than "
                                    + "the maximum numeric primary key "
                                    + maximumId);
                }
            }
        }
    }

    private static Map<String, BundleManifest.Table> manifestByName(BundleManifest manifest) {
        Map<String, BundleManifest.Table> result = new LinkedHashMap<>();
        for (BundleManifest.Table table : manifest.tables()) {
            result.put(table.name(), table);
        }
        return result;
    }

    private static void printDatabase(
            String status, Connection connection, ResolvedSchema schema, Map<String, Long> snapshot)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        System.out.printf(
                Locale.ROOT,
                "%s product=%s version=%s schema=%s tables=%d snapshot=%s%n",
                status,
                metadata.getDatabaseProductName(),
                metadata.getDatabaseProductVersion(),
                schema.displayName(),
                schema.tables().size(),
                snapshot);
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(byte[] bytes) {
        MessageDigest digest = messageDigest();
        return hex(digest.digest(bytes));
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = messageDigest();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private record ResolvedSchema(String displayName, Map<String, TableRef> tables) {}

    private record TableRef(
            String catalog, String schema, String name, String quote, String product) {
        String logicalName() {
            return lower(name);
        }

        String quoted(String identifier) {
            String physical = SchemaCatalog.physicalColumn(product, logicalName(), identifier);
            if (product.toLowerCase(Locale.ROOT).contains("oracle")) {
                physical = physical.toUpperCase(Locale.ROOT);
            }
            return quotedRaw(physical);
        }

        String quotedRaw(String identifier) {
            return quote + identifier.replace(quote, quote + quote) + quote;
        }

        String sqlName() {
            String qualifier = schema;
            if (qualifier == null && product.toLowerCase(Locale.ROOT).contains("mysql")) {
                qualifier = catalog;
            }
            return qualifier == null
                    ? quotedRaw(name)
                    : quotedRaw(qualifier) + "." + quotedRaw(name);
        }
    }

    private record ColumnMeta(
            String name, int jdbcType, boolean nullable, BundleManifest.LogicalType type) {}

    private record KeyColumn(int sequence, String name) {}

    private record TableShape(
            List<ColumnMeta> columns,
            List<String> primaryKey,
            List<BundleManifest.ForeignKey> foreignKeys) {}

    private record ExportedTable(BundleManifest.Table manifest, long maximumNumericPrimaryKey) {}

    private record DigestResult(long rowCount, String sha256) {}
}
