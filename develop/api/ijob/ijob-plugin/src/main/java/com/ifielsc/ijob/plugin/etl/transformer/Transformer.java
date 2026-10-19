// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.transformer;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import java.util.List;

public interface Transformer {
    String type();

    /** Transforms a batch of records. */
    List<DataRecord> transform(List<DataRecord> records, TransformConfig config);

    default List<DataRecord> transform(
            List<DataRecord> records, TransformConfig config, EtlContext context) {
        return transform(records, config);
    }
}
