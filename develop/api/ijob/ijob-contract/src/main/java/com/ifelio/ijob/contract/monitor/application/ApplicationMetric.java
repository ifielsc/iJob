package com.ifelio.ijob.contract.monitor.application;

import com.ifelio.ijob.contract.monitor.metric.ProcessStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationMetric {
    private String name;

    private boolean found;

    /** PID */
    private Long pid;

    private ProcessStatus status;

    /** CPU(%) */
    private Double cpuUsage;

    /** Memory usage in bytes. */
    private Long memoryUsage;

    private LocalDateTime startTime;

    private String command;

    private Integer processCount;
}
