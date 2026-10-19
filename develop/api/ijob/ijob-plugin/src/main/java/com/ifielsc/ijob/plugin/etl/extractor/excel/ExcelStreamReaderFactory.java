package com.ifielsc.ijob.plugin.etl.extractor.excel;

import java.nio.file.Path;

public class ExcelStreamReaderFactory {
    public static ExcelStreamReader create(Path path, ExcelExtractConfig config) {
        ExcelType type = detect(path);

        AbstractStreamingExcelReader reader =
                switch (type) {
                    case XLS -> new XlsStreamReader(path, config);

                    case XLSX -> new XlsxStreamReader(path, config);
                };
        reader.startReading();
        return reader;
    }

    private static ExcelType detect(Path path) {
        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".xls")) {
            return ExcelType.XLS;
        }

        if (name.endsWith(".xlsx")) {
            return ExcelType.XLSX;
        }

        throw new IllegalArgumentException("unsupported excel:" + name);
    }
}
