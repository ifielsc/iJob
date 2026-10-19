package com.ifielsc.ijob.plugin.etl.extractor.excel;

import java.util.Iterator;
import java.util.List;

public interface ExcelStreamReader extends Iterator<List<String>>, AutoCloseable {
    @Override
    void close();
}
