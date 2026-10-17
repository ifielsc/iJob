package com.ifelio.ijob.monitor.agent.collector.application;

import com.ifelio.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;
import com.ifelio.ijob.contract.monitor.metric.ProcessStatus;
import com.ifelio.ijob.monitor.agent.collector.OshiCollectorSupport;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.ProcessMatcher;
import com.ifelio.ijob.monitor.agent.collector.application.matcher.ProcessMatcherManager;
import com.ifelio.ijob.monitor.agent.model.MetricUtils;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import oshi.software.os.OSProcess;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DefaultApplicationMetricCollector implements ApplicationMetricCollector {

    private static final int MAX_COMMAND_LENGTH = 4096;

    private final OshiCollectorSupport support;

    private final ProcessMatcherManager matcherManager;

    private final ProcessSnapshotCache snapshotCache;

    @Override
    public synchronized List<ApplicationMetric> collect(
            List<ApplicationCollectConfig> applications) {

        List<ApplicationMetric> result = new ArrayList<>();

        if (applications == null || applications.isEmpty()) {
            return result;
        }

        List<OSProcess> processes = support.processes();

        for (ApplicationCollectConfig config : applications) {
            result.add(collectApplication(config, processes));
        }

        refreshSnapshots(processes);
        return result;
    }

    private ApplicationMetric collectApplication(
            ApplicationCollectConfig config, List<OSProcess> processes) {
        ProcessMatcher matcher = matcherManager.getMatcher(config.getMatchType());

        if (matcher == null) {
            return ApplicationMetric.builder()
                    .name(config.getName())
                    .found(false)
                    .status(ProcessStatus.UNKNOWN)
                    .build();
        }

        List<OSProcess> matched = new ArrayList<>();

        for (OSProcess process : processes) {
            if (matcher.matches(process, config.getMatchValue())) {
                matched.add(process);
            }
        }

        if (matched.isEmpty()) {
            return ApplicationMetric.builder()
                    .name(config.getName())
                    .found(false)
                    .processCount(0)
                    .status(ProcessStatus.STOPPED)
                    .build();
        }

        return buildMetric(config, matched);
    }

    private ApplicationMetric buildMetric(
            ApplicationCollectConfig config, List<OSProcess> matched) {
        double cpuUsage = 0D;
        long memory = 0L;
        Long startTime = null;
        String command = null;
        Long pid = null;

        for (OSProcess process : matched) {
            int processId = process.getProcessID();
            OSProcess previous = snapshotCache.get(processId);

            if (isSameProcess(previous, process)) {
                cpuUsage += process.getProcessCpuLoadBetweenTicks(previous) * 100D;
            }

            memory += process.getResidentSetSize();

            if (pid == null) {
                pid = (long) process.getProcessID();
            }

            if (command == null) {
                command = bounded(process.getCommandLine());
            }

            if (startTime == null) {
                startTime = process.getStartTime();
            }
        }

        LocalDateTime start = null;

        if (startTime != null && startTime > 0) {
            start =
                    Instant.ofEpochMilli(startTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
        }

        return ApplicationMetric.builder()
                .name(config.getName())
                .found(true)
                .processCount(matched.size())
                .pid(pid)
                .status(ProcessStatus.RUNNING)
                .cpuUsage(MetricUtils.percent(cpuUsage))
                .memoryUsage(memory)
                .startTime(start)
                .command(command)
                .build();
    }

    private void refreshSnapshots(List<OSProcess> processes) {
        Set<Integer> activePids = new HashSet<>();
        for (OSProcess process : processes) {
            activePids.add(process.getProcessID());
            snapshotCache.put(process);
        }
        snapshotCache.retainOnly(activePids);
    }

    private String bounded(String value) {
        if (value == null || value.length() <= MAX_COMMAND_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_COMMAND_LENGTH);
    }

    static boolean isSameProcess(OSProcess previous, OSProcess current) {
        return previous != null && previous.getStartTime() == current.getStartTime();
    }
}
