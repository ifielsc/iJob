// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.extractor.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.StopException;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.file.client.FileInfo;
import com.ifielsc.ijob.plugin.etl.TempDirectoryResource;
import com.ifielsc.ijob.plugin.etl.TempPathResource;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.reader.DataReader;
import com.ifielsc.ijob.plugin.support.RecordingInstanceLogs;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Timeout(30)
class FileSqlExtractorTest {
    @Test
    void shouldReadRealMdbWithBundledDriverWithoutChangingSource() throws Exception {
        Path directory = Files.createTempDirectory("ijob-mdb-driver-test-");
        Path source = directory.resolve("source.mdb");
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            try (Connection connection =
                            DriverManager.getConnection(
                                    "jdbc:ucanaccess://" + source + ";newDatabaseVersion=V2000");
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE qa_source (id TEXT(30), amount TEXT(40))");
                statement.execute(
                        "INSERT INTO qa_source VALUES ('9007199254740993',"
                                + "'12345678901234.123456')");
            }
            byte[] original = Files.readAllBytes(source);
            EtlTaskConfig config = config("mdb");
            config.getSource().setFormat(EtlTaskConfig.FileFormat.MDB);
            config.getSource().setSql("SELECT id AS id, amount AS amount FROM qa_source");
            try (Fixture fixture = new Fixture(config, original)) {
                List<DataRecord> rows = fixture.read();
                assertEquals(1, rows.size());
                assertEquals("9007199254740993", rows.getFirst().get("id"));
                assertEquals("12345678901234.123456", rows.getFirst().get("amount"));
            }
            assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(source)));
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldJoinSheetsWithIndependentPhysicalRowOptionsAndParameters(String format)
            throws Exception {
        byte[] file =
                workbook(
                        format,
                        book -> {
                            Sheet prices = book.createSheet("行情");
                            row(prices, 0, "说明");
                            row(prices, 2, "i_code", "close");
                            row(prices, 4, "11", "3.5");
                            row(prices, 6, "12", "4.5");
                            Sheet securities = book.createSheet("证券");
                            row(securities, 0, "i_code", "name");
                            row(securities, 1, "11", "Alice");
                            row(securities, 2, "12", "Bob");
                        });
        EtlTaskConfig config = config(format);
        EtlTaskConfig.ExcelSheetConfig prices = sheet("行情", "PRICES");
        prices.setHeaderRow(2);
        prices.setStartRow(4);
        EtlTaskConfig.ExcelSheetConfig securities = sheet(null, "SECURITIES");
        securities.setSheetIndex(1);
        config.getSource().setSheets(List.of(prices, securities));
        config.getSource().getParameters().put("minimum", 4);
        config.getSource()
                .setSql(
                        """
                        SELECT p."i_code", s."name", CAST(p."close" AS DECIMAL(10, 2)) AS "close"
                        FROM "PRICES" p JOIN "SECURITIES" s ON p."i_code" = s."i_code"
                        WHERE CAST(p."close" AS DECIMAL(10, 2)) > :minimum
                        """);
        try (Fixture fixture = new Fixture(config, file)) {
            List<DataRecord> result = fixture.read();
            assertEquals(1, result.size());
            assertEquals("12", result.getFirst().get("i_code"));
            assertEquals("Bob", result.getFirst().get("name"));
            assertEquals("4.50", result.getFirst().get("close").toString());
            assertEquals(1, fixture.downloads);
            assertTrue(
                    fixture.logs.messages().stream()
                            .anyMatch(log -> log.contains("tables=2, rows=4")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldUnionSheetsAcrossWriteBatchesAndReadEofRepeatedly(String format) throws Exception {
        byte[] file =
                workbook(
                        format,
                        book -> {
                            Sheet a = book.createSheet("A");
                            row(a, 0, "id");
                            row(a, 1, "1");
                            row(a, 2, "2");
                            Sheet b = book.createSheet("B");
                            row(b, 0, "id");
                            row(b, 1, "3");
                            row(b, 2, "4");
                        });
        try (Fixture fixture = new Fixture(twoSheets(format), file)) {
            assertEquals(
                    List.of("1", "2", "3", "4"),
                    fixture.read().stream().map(r -> r.get("id")).toList());
            assertEquals(1, fixture.downloads);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldInferEmptySheetSchemaFromHeader(String format) throws Exception {
        byte[] file =
                workbook(
                        format,
                        book -> {
                            row(book.createSheet("A"), 0, "id");
                            Sheet b = book.createSheet("B");
                            row(b, 0, "id");
                            row(b, 1, "2");
                        });
        try (Fixture fixture = new Fixture(twoSheets(format), file)) {
            assertEquals("2", fixture.read().getFirst().get("id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldUseExplicitSchemaForCompletelyEmptySheet(String format) throws Exception {
        byte[] file =
                workbook(
                        format,
                        book -> {
                            book.createSheet("A");
                            Sheet b = book.createSheet("B");
                            row(b, 0, "id");
                            row(b, 1, "2");
                        });
        EtlTaskConfig config = twoSheets(format);
        config.getSource().getSheets().getFirst().setStagingColumns(List.of("id"));
        try (Fixture fixture = new Fixture(config, file)) {
            assertEquals("2", fixture.read().getFirst().get("id"));
        }
        config.getSource().getSheets().getFirst().setStagingColumns(List.of());
        try (Fixture fixture = new Fixture(config, file)) {
            assertTrue(
                    assertThrows(IllegalArgumentException.class, fixture::read)
                            .getMessage()
                            .contains("stagingColumns"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldGenerateColumnsForHeaderlessSheet(String format) throws Exception {
        EtlTaskConfig config = config(format);
        EtlTaskConfig.ExcelSheetConfig sheet = sheet("A", "A");
        sheet.setHeader(false);
        sheet.setStartRow(2);
        config.getSource().setSheets(List.of(sheet));
        config.getSource().setSql("SELECT \"F1\" AS \"id\" FROM \"A\"");
        byte[] file = workbook(format, book -> row(book.createSheet("A"), 2, "7"));
        try (Fixture fixture = new Fixture(config, file)) {
            assertEquals("7", fixture.read().getFirst().get("id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldFailOnMissingSheetEvenWithExplicitSchema(String format) throws Exception {
        EtlTaskConfig config = twoSheets(format);
        config.getSource().getSheets().getLast().setStagingColumns(List.of("id"));
        byte[] file =
                workbook(
                        format,
                        book -> {
                            Sheet a = book.createSheet("A");
                            row(a, 0, "id");
                            row(a, 1, "1");
                        });
        try (Fixture fixture = new Fixture(config, file)) {
            Throwable error = assertThrows(IllegalStateException.class, fixture::read);
            assertTrue(rootMessage(error).contains("sheet was not found"));
            assertFalse(fixture.logs.messages().contains("INFO ETL file SQL query started"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldRejectDuplicateHeadersAndDuplicateQueryLabels(String format) throws Exception {
        byte[] invalid = workbook(format, book -> row(book.createSheet("A"), 0, "id", "id"));
        try (Fixture fixture = new Fixture(twoSheets(format), invalid)) {
            assertTrue(
                    assertThrows(IllegalArgumentException.class, fixture::read)
                            .getMessage()
                            .contains("headers"));
        }
        EtlTaskConfig config = twoSheets(format);
        config.getSource().setSql("SELECT * FROM \"A\" CROSS JOIN \"B\"");
        try (Fixture fixture = new Fixture(config, simpleWorkbook(format))) {
            assertTrue(
                    rootMessage(assertThrows(IllegalStateException.class, fixture::read))
                            .contains("explicit aliases"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldShareExcelAndStagingBudgetsAcrossSheets(String format) throws Exception {
        List<Consumer<EtlTaskConfig.ExecutionConfig>> limits =
                List.of(
                        execution -> execution.setMaxExcelCells(3),
                        execution -> execution.setMaxStagingCells(1),
                        execution -> execution.setMaxRecords(3),
                        execution -> execution.setMaxExpandedTextChars(5));
        for (Consumer<EtlTaskConfig.ExecutionConfig> limit : limits) {
            EtlTaskConfig config = twoSheets(format);
            limit.accept(config.getExecution());
            try (Fixture fixture = new Fixture(config, simpleWorkbook(format))) {
                assertTrue(
                        rootMessage(assertThrows(RuntimeException.class, fixture::read))
                                .contains("limit exceeded"));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldKeepLegacySingleSheetSqlConfiguration(String format) throws Exception {
        EtlTaskConfig config = config(format);
        config.getSource().setSheetName("B");
        config.getSource().setSql("SELECT \"id\" FROM \"SOURCE_DATA\"");
        try (Fixture fixture = new Fixture(config, simpleWorkbook(format))) {
            assertEquals("2", fixture.read().getFirst().get("id"));
        }
        config.getSource().setSheetName(null);
        try (Fixture fixture = new Fixture(config, simpleWorkbook(format))) {
            List<DataRecord> rows = fixture.read();
            assertEquals(1, rows.size());
            assertEquals("1", rows.getFirst().get("id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void shouldReadSelectedSheetsAfterWorkbookTabReordering(String format) throws Exception {
        byte[] file =
                workbook(
                        format,
                        book -> {
                            Sheet a = book.createSheet("A");
                            row(a, 0, "id");
                            row(a, 1, "1");
                            Sheet b = book.createSheet("B");
                            row(b, 0, "id");
                            row(b, 1, "2");
                            book.setSheetOrder("B", 0);
                        });
        EtlTaskConfig config = config(format);
        config.getSource().setSheets(List.of(sheet("A", "CHOSEN")));
        config.getSource().setSql("SELECT \"id\" FROM \"CHOSEN\"");
        try (Fixture fixture = new Fixture(config, file)) {
            assertEquals("1", fixture.read().getFirst().get("id"));
        }
        EtlTaskConfig.ExcelSheetConfig selected = sheet(null, "CHOSEN");
        selected.setSheetIndex(0);
        config.getSource().setSheets(List.of(selected));
        try (Fixture fixture = new Fixture(config, file)) {
            assertEquals("2", fixture.read().getFirst().get("id"));
        }
    }

    @Test
    void shouldExecuteBeforeSqlOnlyAfterAllSheetsAreStaged() throws Exception {
        EtlTaskConfig config = twoSheets("xlsx");
        config.getSource()
                .setBeforeSql(
                        List.of(
                                "CREATE VIEW combined AS SELECT * FROM \"A\" UNION ALL SELECT *"
                                        + " FROM \"B\""));
        config.getSource().setSql("SELECT COUNT(*) AS \"total\" FROM combined");
        try (Fixture fixture = new Fixture(config, simpleWorkbook("xlsx"))) {
            assertEquals(2L, fixture.read().getFirst().get("total"));
        }
    }

    @Test
    void shouldBoundTheDownloadedWorkbook() throws Exception {
        EtlTaskConfig config = twoSheets("xlsx");
        config.getExecution().setMaxFileBytes(10);
        try (Fixture fixture = new Fixture(config, simpleWorkbook("xlsx"))) {
            assertTrue(
                    assertThrows(IllegalArgumentException.class, fixture::read)
                            .getMessage()
                            .contains("file limit exceeded"));
        }
    }

    @Test
    void shouldKeepCsvSqlWorking() {
        EtlTaskConfig config = config("csv");
        config.getSource().setFormat(EtlTaskConfig.FileFormat.CSV);
        config.getSource().setSql("SELECT \"id\" FROM \"SOURCE_DATA\"");
        try (Fixture fixture = new Fixture(config, "id\n1\n2\n".getBytes(StandardCharsets.UTF_8))) {
            assertEquals(2, fixture.read().size());
        }
    }

    @Test
    void shouldStopBetweenSheetsAndReleaseResources() throws Exception {
        try (Fixture fixture = new Fixture(twoSheets("xlsx"), simpleWorkbook("xlsx"))) {
            fixture.stopAfterFirstTable = true;
            assertThrows(StopException.class, fixture::read);
            assertFalse(
                    fixture.logs
                            .messages()
                            .contains("INFO ETL Excel sheet staging started, table=B"));
        }
    }

    private EtlTaskConfig twoSheets(String format) {
        EtlTaskConfig config = config(format);
        config.getSource().setSheets(List.of(sheet("A", "A"), sheet("B", "B")));
        config.getSource()
                .setSql(
                        "SELECT \"id\" FROM \"A\" UNION ALL SELECT \"id\" FROM \"B\" ORDER BY"
                                + " \"id\"");
        return config;
    }

    private EtlTaskConfig config(String format) {
        EtlTaskConfig config = new EtlTaskConfig();
        EtlTaskConfig.SourceConfig source = new EtlTaskConfig.SourceConfig();
        source.setType(EtlTaskConfig.SourceType.FILE);
        source.setFormat(EtlTaskConfig.FileFormat.EXCEL);
        source.setReadMode(EtlTaskConfig.FileReadMode.SQL);
        source.setResourceId(1L);
        source.setPath("source." + format);
        config.setSource(source);
        config.getExecution().setWriteBatchSize(1);
        return config;
    }

    private EtlTaskConfig.ExcelSheetConfig sheet(String name, String table) {
        EtlTaskConfig.ExcelSheetConfig sheet = new EtlTaskConfig.ExcelSheetConfig();
        sheet.setSheetName(name);
        sheet.setTableName(table);
        return sheet;
    }

    private byte[] simpleWorkbook(String format) throws Exception {
        return workbook(
                format,
                book -> {
                    Sheet a = book.createSheet("A");
                    row(a, 0, "id");
                    row(a, 1, "1");
                    Sheet b = book.createSheet("B");
                    row(b, 0, "id");
                    row(b, 1, "2");
                });
    }

    private byte[] workbook(String format, Consumer<Workbook> populate) throws Exception {
        try (Workbook book = format.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            populate.accept(book);
            book.write(output);
            return output.toByteArray();
        }
    }

    private void row(Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) {
            row.createCell(column).setCellValue(values[column]);
        }
    }

    private String rootMessage(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error.getMessage();
    }

    private static final class Fixture implements AutoCloseable {
        private final RecordingInstanceLogs logs = new RecordingInstanceLogs();
        private final ExecutionHandle handle = new ExecutionHandle(1L);
        private final TaskContext task;
        private final EtlContext context;
        private final FileSqlExtractor extractor;
        private int downloads;
        private int releases;
        private boolean stopAfterFirstTable;

        private Fixture(EtlTaskConfig config, byte[] bytes) {
            task =
                    new TaskContext(1L, handle, null, logs, new VariableResolver(), Map.of()) {
                        @Override
                        public void logInfo(String message) {
                            super.logInfo(message);
                            if (stopAfterFirstTable
                                    && message.startsWith("ETL file SQL table staged, table=A")) {
                                handle.requestStop();
                            }
                        }
                    };
            context = new EtlContext(task, config);
            FileClient client =
                    new FileClient() {
                        @Override
                        public InputStream download(String path) {
                            downloads++;
                            return new ByteArrayInputStream(bytes);
                        }

                        @Override
                        public List<FileInfo> list(String path) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void upload(String path, InputStream input) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void mkdirs(String path) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public boolean exists(String path) {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public void delete(String path) {
                            throw new UnsupportedOperationException();
                        }
                    };
            extractor =
                    new FileSqlExtractor(
                            new ResourceResolver(null) {
                                @Override
                                public ResourceClientLease<FileClient> leaseFileClient(
                                        Long resourceId) {
                                    return new ResourceClientLease<>(client, () -> releases++);
                                }
                            });
        }

        private List<DataRecord> read() {
            try (DataReader reader = extractor.extract(context)) {
                List<DataRecord> rows = new ArrayList<>();
                while (reader.hasNext()) {
                    rows.add(reader.next());
                }
                assertFalse(reader.hasNext());
                assertNull(reader.next());
                return rows;
            }
        }

        @Override
        public void close() {
            List<Path> paths = new ArrayList<>();
            for (AutoCloseable resource : task.getResources()) {
                if (resource instanceof TempDirectoryResource directory) {
                    paths.add(directory.directory());
                } else if (resource instanceof TempPathResource file) {
                    paths.add(file.path());
                }
            }
            task.cleanupResources();
            paths.forEach(
                    path -> assertFalse(Files.exists(path), "Temporary resource leaked: " + path));
            assertEquals(downloads, releases);
        }
    }
}
