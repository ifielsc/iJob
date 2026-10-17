package com.ifelio.ijob.plugin.etl.extractor.file;

import com.ifelio.ijob.core.resource.ResourceClientLease;
import com.ifelio.ijob.core.resource.ResourceResolver;
import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.plugin.common.jdbc.CancelableJdbcResources;
import com.ifelio.ijob.plugin.etl.TempDirectoryResource;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.extractor.AbstractExtractor;
import com.ifelio.ijob.plugin.etl.extractor.database.JdbcDataReader;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelDataReader;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelExtractConfig;
import com.ifelio.ijob.plugin.etl.extractor.excel.ExcelReadQuota;
import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.reader.DataReader;
import com.ifelio.ijob.plugin.etl.sql.DriverDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

public final class FileSqlExtractor extends AbstractExtractor {
    private final ResourceResolver resourceResolver;

    public FileSqlExtractor(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Override
    public DataReader extract(EtlContext context) {
        return switch (context.getConfig().getSource().getFormat()) {
            case SQLITE -> openDatabaseFile(context, "org.sqlite.JDBC", "jdbc:sqlite:");
            case MDB ->
                    openDatabaseFile(
                            context, "net.ucanaccess.jdbc.UcanaccessDriver", "jdbc:ucanaccess://");
            case CSV, TXT, EXCEL -> openStagedTable(context);
            case REGEX ->
                    throw new IllegalArgumentException(
                            "ETL regex source does not support SQL mode");
        };
    }

    private DataReader openDatabaseFile(EtlContext context, String driver, String urlPrefix) {
        Path file = stageSourceFile(context);
        DataSource dataSource = new DriverDataSource(driver, urlPrefix + file.toAbsolutePath());
        return openQuery(dataSource, context);
    }

    private DataReader openStagedTable(EtlContext context) {
        TempDirectoryResource directory = createTempDirectory(context);
        String databasePath = directory.directory().resolve("source").toAbsolutePath().toString();
        DataSource dataSource =
                new DriverDataSource(
                        "org.h2.Driver", "jdbc:h2:file:" + databasePath + ";DB_CLOSE_DELAY=0");
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        StagingQuota quota = new StagingQuota(context.getConfig().getExecution());
        FileExtractor extractor = new FileExtractor(resourceResolver);
        if (!config.getSheets().isEmpty()) {
            stageExcelSheets(dataSource, extractor, quota, context);
        } else {
            try (DataReader source = extractor.extractDirect(context)) {
                stageRows(
                        dataSource,
                        source,
                        config.getStagingTable(),
                        config.getStagingColumns(),
                        quota,
                        context);
            }
        }
        context.getTaskContext()
                .logInfo(
                        "ETL file SQL staging completed, tables="
                                + Math.max(1, config.getSheets().size())
                                + ", rows="
                                + quota.rows()
                                + ", cells="
                                + quota.cells());
        context.checkControl();
        return openQuery(dataSource, context);
    }

    private void stageExcelSheets(
            DataSource dataSource,
            FileExtractor extractor,
            StagingQuota quota,
            EtlContext context) {
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        Path workbook = extractor.stageExcel(context);
        ExcelReadQuota excelQuota =
                new ExcelReadQuota(
                        FileExtractor.excelConfig(config, context.getConfig().getExecution()));
        for (EtlTaskConfig.ExcelSheetConfig sheet : config.getSheets()) {
            context.checkControl();
            ExcelExtractConfig excel =
                    FileExtractor.excelConfig(config, context.getConfig().getExecution());
            excel.setSheetName(sheet.getSheetName());
            excel.setSheetIndex(sheet.getSheetIndex());
            excel.setHeader(sheet.isHeader());
            excel.setHeaderRow(sheet.getHeaderRow());
            excel.setStartRow(sheet.getStartRow());
            context.getTaskContext()
                    .logInfo("ETL Excel sheet staging started, table=" + sheet.getTableName());
            try (ExcelDataReader reader =
                    FileExtractor.openExcel(workbook, excel, excelQuota, context)) {
                stageRows(
                        dataSource,
                        reader,
                        sheet.getTableName(),
                        sheet.getStagingColumns(),
                        quota,
                        context);
            }
        }
    }

    private void stageRows(
            DataSource dataSource,
            DataReader source,
            String table,
            List<String> emptyColumns,
            StagingQuota quota,
            EtlContext context) {
        long started = System.nanoTime();
        long initialRows = quota.rows();
        context.checkControl();
        EtlTaskConfig.ExecutionConfig execution = context.getConfig().getExecution();
        DataRecord first = source.hasNext() ? source.next() : null;
        List<String> columns =
                first == null
                        ? source instanceof ExcelDataReader excel && !excel.getHeaders().isEmpty()
                                ? excel.getHeaders()
                                : emptyColumns
                        : List.copyOf(first.getFields().keySet());
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "ETL stagingColumns are required for an empty SQL file " + "source");
        }
        if (columns.size() > execution.getMaxRecordFields()) {
            throw new IllegalArgumentException("ETL staging field limit exceeded");
        }
        if (first != null) {
            quota.accept(first);
        }
        CancelableJdbcResources resources = new CancelableJdbcResources();
        context.getTaskContext().registerResource(resources);
        try {
            Connection connection = dataSource.getConnection();
            resources.attach(connection);
            connection.setAutoCommit(false);
            createStagingTable(
                    connection,
                    table,
                    columns,
                    execution.getMaxTextFieldChars(),
                    resources,
                    execution.getQueryTimeoutSeconds());
            String sql = insertSql(table, columns);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                resources.attach(statement);
                statement.setQueryTimeout(execution.getQueryTimeoutSeconds());
                int pending = 0;
                if (first != null) {
                    bindRow(statement, first, columns);
                    statement.addBatch();
                    pending++;
                }
                while (source.hasNext()) {
                    context.checkControl();
                    DataRecord record = source.next();
                    if (record == null) {
                        continue;
                    }
                    quota.accept(record);
                    bindRow(statement, record, columns);
                    statement.addBatch();
                    if (++pending >= context.getConfig().getExecution().getWriteBatchSize()) {
                        statement.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    context.checkControl();
                    statement.executeBatch();
                }
            }
            context.checkControl();
            connection.commit();
            context.getTaskContext()
                    .logInfo(
                            "ETL file SQL table staged, table="
                                    + table
                                    + ", rows="
                                    + (quota.rows() - initialRows)
                                    + ", elapsedMillis="
                                    + (System.nanoTime() - started) / 1_000_000);
        } catch (SQLException exception) {
            throw new IllegalStateException("ETL tabular file staging failed", exception);
        } finally {
            resources.close();
        }
    }

    private void createStagingTable(
            Connection connection,
            String table,
            List<String> columns,
            int maxTextFieldChars,
            CancelableJdbcResources resources,
            int timeout)
            throws SQLException {
        String definitions =
                columns.stream()
                        .map(column -> quoted(column) + " VARCHAR(" + maxTextFieldChars + ")")
                        .collect(java.util.stream.Collectors.joining(","));
        try (Statement statement = connection.createStatement()) {
            resources.attach(statement);
            statement.setQueryTimeout(timeout);
            statement.execute("CREATE TABLE " + quoted(table) + " (" + definitions + ")");
        }
    }

    private String insertSql(String table, List<String> columns) {
        String names =
                columns.stream()
                        .map(this::quoted)
                        .collect(java.util.stream.Collectors.joining(","));
        String values = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
        return "INSERT INTO " + quoted(table) + " (" + names + ") VALUES (" + values + ")";
    }

    private void bindRow(PreparedStatement statement, DataRecord record, List<String> columns)
            throws SQLException {
        if (!record.getFields().keySet().equals(new java.util.LinkedHashSet<>(columns))) {
            throw new IllegalArgumentException("ETL staged file columns changed between rows");
        }
        for (int index = 0; index < columns.size(); index++) {
            Object value = record.get(columns.get(index));
            statement.setString(index + 1, value == null ? null : value.toString());
        }
    }

    private DataReader openQuery(DataSource dataSource, EtlContext context) {
        context.getTaskContext().logInfo("ETL file SQL query started");
        Map<String, Object> parameters = new LinkedHashMap<>();
        context.getConfig()
                .getSource()
                .getParameters()
                .forEach(
                        (name, value) ->
                                parameters.put(
                                        name, context.getTaskContext().resolveObject(value)));
        return JdbcDataReader.open(
                dataSource, context.getConfig().getSource(), context, parameters);
    }

    private Path stageSourceFile(EtlContext context) {
        if (context.getStagedSource() != null) return context.getStagedSource();
        TempDirectoryResource directory = createTempDirectory(context);
        EtlTaskConfig.SourceConfig config = context.getConfig().getSource();
        String sourcePath = context.getTaskContext().resolve(config.getPath());
        String name =
                config.getFormat() == EtlTaskConfig.FileFormat.SQLITE
                        ? "source.sqlite"
                        : "source.mdb";
        Path target = directory.directory().resolve(name);
        ResourceClientLease<FileClient> lease =
                resourceResolver.leaseFileClient(config.getResourceId());
        context.getTaskContext().registerResource(lease);
        try (InputStream input = lease.getClient().download(sourcePath);
                OutputStream output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > context.getConfig().getExecution().getMaxFileBytes()) {
                    throw new IllegalArgumentException("ETL source file limit exceeded");
                }
                output.write(buffer, 0, read);
            }
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("ETL database file staging failed", exception);
        }
    }

    private TempDirectoryResource createTempDirectory(EtlContext context) {
        try {
            TempDirectoryResource resource =
                    new TempDirectoryResource(Files.createTempDirectory("ijob-etl-sql-"));
            context.getTaskContext().registerResource(resource);
            return resource;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "ETL temporary directory could not be created", exception);
        }
    }

    private String quoted(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
