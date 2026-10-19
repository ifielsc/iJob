package com.ifielsc.ijob.plugin.etl.loader;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.util.List;

public interface Loader extends AutoCloseable {
    void open(EtlContext context);

    LoadBatchResult writeBatch(List<DataRecord> records);

    void flush();

    @Override
    void close();
}
