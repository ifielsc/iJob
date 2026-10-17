package com.ifelio.ijob.plugin.servermonitor;

import com.ifelio.ijob.contract.monitor.CollectType;
import com.ifelio.ijob.contract.monitor.MonitorCollectRequest;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.contract.monitor.application.ApplicationCollectConfig;
import com.ifelio.ijob.contract.monitor.application.ApplicationMetric;
import com.ifelio.ijob.contract.monitor.metric.DiskMetric;
import com.ifelio.ijob.contract.monitor.metric.NetworkMetric;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifelio.ijob.plugin.servermonitor.rule.ApplicationMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.NetworkMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.Threshold;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MonitorReportValidator {
    private static final int MAX_DISKS = 256;
    private static final int MAX_NETWORKS = 256;
    private static final int MAX_APPLICATIONS = 64;
    private static final int MAX_EXTENSIONS = 64;
    private static final Duration MAX_REPORT_AGE = Duration.ofMinutes(5);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(1);
    private static final long MAX_ELAPSED_MILLIS = 300_000L;

    private final Clock clock;

    public MonitorReportValidator() {
        this(Clock.systemUTC());
    }

    MonitorReportValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(
            MonitorReport report, MonitorCollectRequest request, MonitorConfig config) {
        validateRequest(request, config);
        if (report == null || !report.isSuccess()) {
            throw new IllegalStateException("monitor agent collection was unsuccessful");
        }

        validateMetadata(report);

        Set<CollectType> items = new HashSet<>(request.getCollectItems());
        if (items.contains(CollectType.CPU)) {
            if (report.getCpu() == null) {
                throw incomplete("cpu");
            }
            validatePercentage(report.getCpu().getUsage(), "cpu usage");
        }
        if (items.contains(CollectType.MEMORY)) {
            if (report.getMemory() == null) {
                throw incomplete("memory");
            }
            validatePercentage(report.getMemory().getUsage(), "memory usage");
        }
        if (items.contains(CollectType.DISK)) {
            validateDisks(report.getDisks(), config);
        }
        if (items.contains(CollectType.NETWORK)) {
            validateNetworks(report.getNetworks(), config);
        }
        if (items.contains(CollectType.APPLICATION)) {
            validateApplications(report.getApplications(), request.getApplications(), config);
        }
    }

    private void validateDisks(List<DiskMetric> disks, MonitorConfig config) {
        if (disks == null) {
            throw incomplete("disk");
        }
        if (disks.size() > MAX_DISKS) {
            throw incomplete("disk count");
        }
        Set<String> paths = new HashSet<>();
        DiskMonitorRule rule = config.getRules().getDisk();
        if (disks.isEmpty() && rule != null && rule.isEnabled()) {
            throw incomplete("disk");
        }
        for (DiskMetric disk : disks) {
            String normalizedPath =
                    disk == null ? null : DiskPathNormalizer.normalize(disk.getPath());
            if (disk == null
                    || disk.getPath() == null
                    || disk.getPath().isBlank()
                    || disk.getPath().length() > 512
                    || !paths.add(normalizedPath)) {
                throw incomplete("disk");
            }
            Threshold threshold = rule == null ? null : resolveDiskThreshold(normalizedPath, rule);
            if (disk.getUsage() != null || threshold != null) {
                validatePercentage(disk.getUsage(), "disk usage");
            }
        }
        if (rule != null && rule.isEnabled() && rule.getPaths() != null) {
            for (String requiredPath : rule.getPaths().keySet()) {
                if (!paths.contains(DiskPathNormalizer.normalize(requiredPath))) {
                    throw incomplete("configured disk path");
                }
            }
        }
    }

    private Threshold resolveDiskThreshold(String path, DiskMonitorRule rule) {
        String normalizedPath = DiskPathNormalizer.normalize(path);
        if (rule.getPaths() != null) {
            for (Map.Entry<String, Threshold> entry : rule.getPaths().entrySet()) {
                if (normalizedPath.equals(DiskPathNormalizer.normalize(entry.getKey()))) {
                    return entry.getValue();
                }
            }
        }
        return rule.getUsage();
    }

    private void validateRequest(MonitorCollectRequest request, MonitorConfig config) {
        if (request == null
                || request.getCollectItems() == null
                || request.getCollectItems().isEmpty()) {
            throw new IllegalArgumentException("monitor collect request is required");
        }
        if (request.getCollectItems().stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("monitor collect request contains null item");
        }
        if (config == null || config.getRules() == null) {
            throw new IllegalArgumentException("monitor rules are required");
        }
    }

    private void validateNetworks(List<NetworkMetric> networks, MonitorConfig config) {
        if (networks == null) {
            throw incomplete("network");
        }
        if (networks.size() > MAX_NETWORKS) {
            throw incomplete("network count");
        }
        Set<String> names = new HashSet<>();
        NetworkMonitorRule rule = config.getRules().getNetwork();
        if (networks.isEmpty() && rule != null && rule.isEnabled()) {
            throw incomplete("network");
        }
        for (NetworkMetric network : networks) {
            if (network == null
                    || network.getName() == null
                    || network.getName().isBlank()
                    || network.getName().length() > 128
                    || !names.add(network.getName())) {
                throw incomplete("network");
            }
            if (network.getReceiveRate() != null
                    || (rule != null && rule.getReceiveRate() != null)) {
                validateRate(network.getReceiveRate(), "network receive");
            }
            if (network.getSendRate() != null || (rule != null && rule.getSendRate() != null)) {
                validateRate(network.getSendRate(), "network send");
            }
        }
    }

    private void validateApplications(
            List<ApplicationMetric> metrics,
            List<ApplicationCollectConfig> collections,
            MonitorConfig config) {
        if (metrics == null) {
            throw incomplete("application");
        }
        if (metrics.size() > MAX_APPLICATIONS) {
            throw incomplete("application count");
        }
        Map<String, ApplicationMetric> byName = new HashMap<>();
        for (ApplicationMetric metric : metrics) {
            if (metric == null
                    || metric.getName() == null
                    || metric.getName().isBlank()
                    || metric.getName().length() > 128
                    || byName.putIfAbsent(metric.getName(), metric) != null) {
                throw incomplete("application");
            }
        }
        Map<String, ApplicationMonitorRule> rules = new HashMap<>();
        if (config.getRules().getApplications() != null) {
            for (ApplicationMonitorRule rule : config.getRules().getApplications()) {
                rules.put(rule.getName(), rule);
            }
        }
        if (collections == null) {
            return;
        }
        for (ApplicationCollectConfig collection : collections) {
            ApplicationMetric metric = byName.get(collection.getName());
            if (metric == null) {
                throw incomplete("application");
            }
            if (metric.isFound()
                    && (metric.getProcessCount() == null || metric.getProcessCount() <= 0)) {
                throw incomplete("application process count");
            }
            if (metric.getCommand() != null && metric.getCommand().length() > 4096) {
                throw incomplete("application command");
            }
            if (!metric.isFound()
                    && metric.getProcessCount() != null
                    && metric.getProcessCount() != 0) {
                throw incomplete("application process count");
            }
            ApplicationMonitorRule rule = rules.get(collection.getName());
            if (!metric.isFound() || rule == null || !rule.isEnabled()) {
                continue;
            }
            if (rule.getCpu() != null) {
                validatePercentage(metric.getCpuUsage(), "application cpu usage");
            }
            if (rule.getMemory() != null
                    && (metric.getMemoryUsage() == null || metric.getMemoryUsage() < 0)) {
                throw incomplete("application memory usage");
            }
        }
        if (byName.size() != collections.size()) {
            throw incomplete("application count");
        }
    }

    private void validateMetadata(MonitorReport report) {
        if (report.getAgent() == null
                || report.getAgent().getVersion() == null
                || report.getAgent().getVersion().isBlank()
                || report.getAgent().getVersion().length() > 64
                || report.getAgent().getHostname() == null
                || report.getAgent().getHostname().isBlank()
                || report.getAgent().getHostname().length() > 255
                || report.getAgent().getOsType() == null
                || report.getAgent().getStartTime() == null) {
            throw incomplete("agent identity");
        }
        if (report.getElapsed() == null
                || report.getElapsed() < 0
                || report.getElapsed() > MAX_ELAPSED_MILLIS) {
            throw incomplete("collection elapsed");
        }
        if (report.getCollectTime() == null) {
            throw incomplete("collection time");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (report.getCollectTime().isBefore(now.minus(MAX_REPORT_AGE))
                || report.getCollectTime().isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw incomplete("collection time");
        }
        if (report.getExtensions() != null && report.getExtensions().size() > MAX_EXTENSIONS) {
            throw incomplete("extension count");
        }
    }

    private void validatePercentage(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 100) {
            throw incomplete(name);
        }
    }

    private void validateRate(Long value, String name) {
        if (value == null || value < 0) {
            throw incomplete(name);
        }
    }

    private IllegalStateException incomplete(String item) {
        return new IllegalStateException("monitor report is incomplete: " + item);
    }
}
