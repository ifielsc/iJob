package com.ifelio.ijob.contract.monitor;

import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;
import com.ifelio.ijob.contract.monitor.metric.CpuMetric;
import com.ifelio.ijob.contract.monitor.metric.DiskMetric;
import com.ifelio.ijob.contract.monitor.metric.MemoryMetric;
import com.ifelio.ijob.contract.monitor.metric.NetworkMetric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorReport {
    private AgentInfo agent;

    /** Collection completion time in UTC. */
    private LocalDateTime collectTime;

    private boolean success;

    /** Failure reason when collection is unsuccessful. */
    private String message;

    /** Collection duration in milliseconds. */
    private Long elapsed;

    /** CPU */
    private CpuMetric cpu;

    private MemoryMetric memory;

    @Builder.Default private List<DiskMetric> disks = new ArrayList<>();

    @Builder.Default private List<NetworkMetric> networks = new ArrayList<>();

    @Builder.Default private List<ApplicationMetric> applications = new ArrayList<>();

    /** Extension metrics, such as GPU, Docker or JVM data. */
    @Builder.Default private Map<String, Object> extensions = new LinkedHashMap<>();
}
