// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.filetransfer;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.zip.Deflater;

@Getter
@AllArgsConstructor
public enum CompressionLevel {
    DEFAULT(Deflater.DEFAULT_COMPRESSION),
    FAST(Deflater.BEST_SPEED),
    BEST(Deflater.BEST_COMPRESSION);

    private final int level;
}
