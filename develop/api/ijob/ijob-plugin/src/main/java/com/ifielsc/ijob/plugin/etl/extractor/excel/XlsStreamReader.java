package com.ifielsc.ijob.plugin.etl.extractor.excel;

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.eventusermodel.MissingRecordAwareHSSFListener;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class XlsStreamReader extends AbstractStreamingExcelReader {
    private final Path path;
    private final ExcelExtractConfig config;

    public XlsStreamReader(Path path, ExcelExtractConfig config) {
        super(5000);
        this.path = path;
        this.config = config;
    }

    @Override
    protected void parse() throws Exception {
        HssfEventListener listener = new HssfEventListener(this::emit, config);
        MissingRecordAwareHSSFListener missing = new MissingRecordAwareHSSFListener(listener);
        FormatTrackingHSSFListener format = new FormatTrackingHSSFListener(missing);
        listener.setFormatListener(format);
        HSSFRequest request = new HSSFRequest();
        request.addListenerForAllRecords(format);
        try (InputStream input = Files.newInputStream(path);
                POIFSFileSystem fileSystem = new POIFSFileSystem(input)) {
            new HSSFEventFactory().processWorkbookEvents(request, fileSystem);
            listener.requireMatchedSheet();
        }
    }
}
