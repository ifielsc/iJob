package com.ifelio.ijob.plugin.filetransfer;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransferMode {
    /** Reads from the source and writes to the target; the default mode. */
    TRANSFER,

    /** Reads and stages source files without publishing to a target. */
    READ_ONLY,

    /** Reserved; task configuration validation rejects this mode. */
    WRITE_ONLY
}
