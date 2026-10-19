// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.reader;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;

public interface DataReader extends AutoCloseable {
    boolean hasNext();

    DataRecord next();

    @Override
    default void close() {}
}
