// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.push.reader;

import java.util.Map;

public interface DataReader extends AutoCloseable {
    boolean hasNext();

    Map<String, Object> next();

    default void close() {}
}
