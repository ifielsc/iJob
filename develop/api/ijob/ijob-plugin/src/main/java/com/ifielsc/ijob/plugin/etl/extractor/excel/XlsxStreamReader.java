package com.ifielsc.ijob.plugin.etl.extractor.excel;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class XlsxStreamReader extends AbstractStreamingExcelReader {
    private final Path path;
    private final ExcelExtractConfig config;

    public XlsxStreamReader(Path path, ExcelExtractConfig config) {
        super(5000);
        this.path = path;
        this.config = config;
    }

    @Override
    protected void parse() throws Exception {
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            SharedStrings strings = reader.getSharedStringsTable();
            validateSharedStrings(strings);
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            int sheetIndex = 0;
            while (sheets.hasNext()) {
                if (sheetIndex >= config.getMaxSheets()) {
                    throw new IllegalArgumentException("ETL Excel sheet limit exceeded");
                }
                try (InputStream stream = sheets.next()) {
                    String name = sheets.getSheetName();
                    if (!matches(name, sheetIndex++)) {
                        continue;
                    }
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(
                            new XSSFSheetXMLHandler(
                                    styles,
                                    null,
                                    strings,
                                    new RowHandler(this::emit),
                                    new DataFormatter(),
                                    false));
                    xmlReader.parse(new InputSource(stream));
                    return;
                }
            }
            throw new IllegalArgumentException("ETL Excel sheet was not found");
        }
    }

    private void validateSharedStrings(SharedStrings strings) {
        if (strings == null) {
            return;
        }
        long total = 0;
        for (int index = 0; index < strings.getUniqueCount(); index++) {
            String value = strings.getItemAt(index).getString();
            int length = value == null ? 0 : value.length();
            if (length > config.getMaxTextFieldChars()
                    || length > config.getMaxExpandedTextChars()
                    || total > config.getMaxExpandedTextChars() - length) {
                throw new IllegalArgumentException("ETL Excel shared string limit exceeded");
            }
            total += length;
        }
    }

    private boolean matches(String name, int index) {
        if (config.getSheetName() != null && !config.getSheetName().isBlank()) {
            return config.getSheetName().equals(name);
        }
        return (config.getSheetIndex() == null ? 0 : config.getSheetIndex()) == index;
    }

    private static final class RowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final java.util.function.Consumer<List<String>> consumer;
        private List<String> values;
        private int nextRow;

        private RowHandler(java.util.function.Consumer<List<String>> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            while (nextRow < rowNum) {
                consumer.accept(List.of());
                nextRow++;
            }
            nextRow = rowNum + 1;
            values = new ArrayList<>();
        }

        @Override
        public void endRow(int rowNum) {
            consumer.accept(values);
        }

        @Override
        public void cell(
                String cellReference,
                String formattedValue,
                org.apache.poi.xssf.usermodel.XSSFComment comment) {
            int column =
                    cellReference == null
                            ? values.size()
                            : new CellReference(cellReference).getCol();
            while (values.size() < column) {
                values.add("");
            }
            values.add(formattedValue);
        }
    }
}
