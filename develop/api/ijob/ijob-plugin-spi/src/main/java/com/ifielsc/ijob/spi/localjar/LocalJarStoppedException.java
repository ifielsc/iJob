// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.spi.localjar;

public final class LocalJarStoppedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocalJarStoppedException() {
        super("Local JAR task was stopped", null, false, false);
    }
}
