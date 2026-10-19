// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.extractor.file;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class LimitedInputStream extends FilterInputStream {
    private final long limit;
    private long count;

    LimitedInputStream(InputStream input, long limit) {
        super(input);
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            add(1);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = super.read(buffer, offset, length);
        if (read > 0) {
            add(read);
        }
        return read;
    }

    private void add(int value) {
        count += value;
        if (count > limit) {
            throw new IllegalArgumentException("ETL source file limit exceeded");
        }
    }
}
