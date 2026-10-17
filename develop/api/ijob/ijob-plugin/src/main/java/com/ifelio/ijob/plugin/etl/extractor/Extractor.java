package com.ifelio.ijob.plugin.etl.extractor;

import com.ifelio.ijob.plugin.etl.model.EtlContext;
import com.ifelio.ijob.plugin.etl.reader.DataReader;

public interface Extractor {
    DataReader extract(EtlContext context);
}
