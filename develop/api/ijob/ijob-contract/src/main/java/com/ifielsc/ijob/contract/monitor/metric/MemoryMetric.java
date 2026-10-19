package com.ifielsc.ijob.contract.monitor.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryMetric {
    /** Total memory in bytes. */
    private Long total;

    /** Used memory in bytes. */
    private Long used;

    /** Free memory in bytes. */
    private Long free;

    /** Available memory in bytes. */
    private Long available;

    /** Memory usage as a percentage. */
    private Double usage;
}
