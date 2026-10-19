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
public class CpuMetric {
    private Integer cores;

    /** CPU usage as a percentage. */
    private Double usage;

    /** User CPU time as a percentage. */
    private Double user;

    /** System CPU time as a percentage. */
    private Double system;

    /** Idle CPU time as a percentage. */
    private Double idle;

    /** One-minute load average. */
    private Double load1;

    /** Five-minute load average. */
    private Double load5;

    /** Fifteen-minute load average. */
    private Double load15;
}
