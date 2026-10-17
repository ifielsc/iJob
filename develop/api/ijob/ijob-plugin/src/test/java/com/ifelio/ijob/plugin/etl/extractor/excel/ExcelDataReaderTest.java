package com.ifelio.ijob.plugin.etl.extractor.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.plugin.etl.model.DataRecord;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

@Timeout(15)
class ExcelDataReaderTest {
    @TempDir Path tempDir;

    @Test
    void duplicateZipNamesAreRejectedBeforeWorkbookParsing() throws Exception {
        Path valid = tempDir.resolve("valid.xlsx");
        try (var workbook = new XSSFWorkbook();
                var out = Files.newOutputStream(valid)) {
            workbook.createSheet("data").createRow(0).createCell(0).setCellValue("ok");
            workbook.write(out);
        }
        Path malicious = tempDir.resolve("duplicate.xlsx");
        try (var zip = new java.util.zip.ZipFile(valid.toFile());
                var out =
                        new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(
                                malicious)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                out.putArchiveEntry(
                        new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(
                                entry.getName()));
                try (var in = zip.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeArchiveEntry();
            }
            out.putArchiveEntry(
                    new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(
                            "xl/workbook.xml"));
            try (var in = zip.getInputStream(zip.getEntry("xl/workbook.xml"))) {
                in.transferTo(out);
            }
            out.closeArchiveEntry();
        }
        var failure =
                assertThrows(
                        Exception.class,
                        () -> {
                            try (var ignored =
                                    org.apache.poi.openxml4j.opc.OPCPackage.open(
                                            malicious.toFile())) {}
                        });
        org.junit.jupiter.api.Assertions.assertTrue(
                failure.getMessage().toLowerCase(java.util.Locale.ROOT).contains("duplicate"));
    }

    @Test
    void updatedPoiPreservesCachedFormulaAndDateFormatting() throws Exception {
        Path file = tempDir.resolve("formula-date.xlsx");
        try (var workbook = new XSSFWorkbook();
                var out = Files.newOutputStream(file)) {
            var row = workbook.createSheet("data").createRow(0);
            row.createCell(0).setCellFormula("1+2");
            row.createCell(1).setCellValue(java.time.LocalDate.of(2026, 1, 2));
            var style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            row.getCell(1).setCellStyle(style);
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(out);
        }
        var config = new ExcelExtractConfig();
        config.setHeader(false);
        try (var reader = ExcelStreamReaderFactory.create(file, config)) {
            assertEquals(List.of("3", "2026-01-02"), reader.next());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void highlyCompressedWorksheetIsRejectedByPoiZipBombGuard() throws Exception {
        Path file = tempDir.resolve("bomb.xlsx");
        try (var workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100);
                var out = Files.newOutputStream(file)) {
            var sheet = workbook.createSheet("data");
            for (int index = 0; index < 3000; index++)
                sheet.createRow(index).createCell(0).setCellValue("A".repeat(1000));
            workbook.write(out);
        }
        var config = new ExcelExtractConfig();
        config.setHeader(false);
        assertThrows(
                RuntimeException.class,
                () -> {
                    try (var reader = ExcelStreamReaderFactory.create(file, config)) {
                        while (reader.hasNext()) reader.next();
                    }
                });
    }

    @Test
    void shouldReadXlsxSharedStringsAndMissingCells() throws Exception {
        assertWorkbook(new XSSFWorkbook(), tempDir.resolve("source.xlsx"));
    }

    @Test
    void shouldReadXlsSharedStringsAndMissingCells() throws Exception {
        assertWorkbook(new HSSFWorkbook(), tempDir.resolve("source.xls"));
    }

    @Test
    void shouldEnforceExpandedTextAndCellLimits() {
        ExcelExtractConfig textConfig = new ExcelExtractConfig();
        textConfig.setHeader(false);
        textConfig.setMaxExpandedTextChars(3);
        try (ExcelDataReader reader =
                new ExcelDataReader(rows(List.of(List.of("ab"), List.of("cd"))), textConfig)) {
            reader.next();
            assertThrows(IllegalArgumentException.class, reader::next);
        }

        ExcelExtractConfig cellConfig = new ExcelExtractConfig();
        cellConfig.setHeader(false);
        cellConfig.setMaxCells(1);
        try (ExcelDataReader reader =
                new ExcelDataReader(rows(List.of(List.of("a", "b"))), cellConfig)) {
            assertThrows(IllegalArgumentException.class, reader::next);
        }
    }

    private ExcelStreamReader rows(List<List<String>> values) {
        Iterator<List<String>> iterator = values.iterator();
        return new ExcelStreamReader() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<String> next() {
                return iterator.next();
            }

            @Override
            public void close() {}
        };
    }

    private void assertWorkbook(Workbook workbook, Path file) throws Exception {
        try (workbook;
                OutputStream output = Files.newOutputStream(file)) {
            Sheet ignored = workbook.createSheet("ignored");
            ignored.createRow(0).createCell(0).setCellValue("wrong");
            Sheet sheet = workbook.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("id");
            header.createCell(1).setCellValue("empty");
            header.createCell(2).setCellValue("name");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(1);
            data.createCell(2).setCellValue("Alice");
            workbook.write(output);
        }
        ExcelExtractConfig config = new ExcelExtractConfig();
        config.setSheetName("data");
        try (ExcelDataReader reader =
                new ExcelDataReader(ExcelStreamReaderFactory.create(file, config), config)) {
            DataRecord record = reader.next();

            assertEquals("1", record.get("id"));
            assertEquals("", record.get("empty"));
            assertEquals("Alice", record.get("name"));
            assertFalse(reader.hasNext());
            assertFalse(reader.hasNext());
            assertNull(reader.next());
        }
    }
}
