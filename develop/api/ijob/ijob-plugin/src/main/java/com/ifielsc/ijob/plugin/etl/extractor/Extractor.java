package com.ifielsc.ijob.plugin.etl.extractor;

import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.reader.DataReader;

public interface Extractor {
    DataReader extract(EtlContext context);
}
