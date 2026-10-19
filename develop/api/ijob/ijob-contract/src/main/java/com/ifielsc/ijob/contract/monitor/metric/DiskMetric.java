// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.monitor.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiskMetric {
    /** Filesystem mount point. */
    private String path;

    private String fileSystem;

    private String label;

    private String type;

    /** Total capacity in bytes. */
    private Long total;

    /** Used capacity in bytes. */
    private Long used;

    /** Free capacity in bytes. */
    private Long free;

    /** Used capacity as a percentage. */
    private Double usage;
}
