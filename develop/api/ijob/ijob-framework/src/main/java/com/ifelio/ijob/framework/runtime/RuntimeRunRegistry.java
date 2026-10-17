package com.ifelio.ijob.framework.runtime;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.infra.mapper.coordination.DatabaseClockMapper;
import com.ifelio.ijob.infra.mapper.runtime.AppRunMapper;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@DependsOn("singletonDatabaseLock")
public class RuntimeRunRegistry implements DisposableBean {
    private static final int RUNTIME_PROTOCOL_VERSION = 10;
    private final AppRunMapper appRunMapper;
    private final DatabaseClockMapper databaseClockMapper;
    private final AppProperties appProperties;
    private final RuntimeSafetyCoordinator safetyCoordinator;
    private volatile String runId;

    @Autowired
    public RuntimeRunRegistry(
            AppRunMapper appRunMapper,
            DatabaseClockMapper databaseClockMapper,
            AppProperties appProperties,
            RuntimeSafetyCoordinator safetyCoordinator) {
        this.appRunMapper = appRunMapper;
        this.databaseClockMapper = databaseClockMapper;
        this.appProperties = appProperties;
        this.safetyCoordinator = safetyCoordinator;
    }

    RuntimeRunRegistry(
            AppRunMapper appRunMapper,
            DatabaseClockMapper databaseClockMapper,
            AppProperties appProperties) {
        this(appRunMapper, databaseClockMapper, appProperties, null);
    }

    public void begin() {
        if (!persistent()) {
            return;
        }
        String newRunId = UUID.randomUUID().toString();
        long processId = ProcessHandle.current().pid();
        String hostName = hostName();
        if (appRunMapper.insertStarting(
                        newRunId,
                        instanceName(hostName, processId),
                        appProperties.getRuntime().getMode().name(),
                        roles(),
                        String.join(",", appProperties.getRuntime().getGroups()),
                        joinIds(appProperties.getRuntime().getAllowedTaskIds()),
                        joinIds(appProperties.getRuntime().getAllowedJobIds()),
                        RUNTIME_PROTOCOL_VERSION,
                        applicationVersion(),
                        hostName,
                        processId,
                        databaseClockMapper.currentTimestamp())
                != 1) {
            throw new IllegalStateException("Application run registration failed");
        }
        runId = newRunId;
    }

    public void markRunning() {
        String current = runId;
        if (current != null && appRunMapper.markRunning(current) != 1) {
            throw new IllegalStateException("Application run cannot enter running state");
        }
    }

    public void markDraining() {
        String current = runId;
        if (current != null && appRunMapper.markDraining(current) != 1) {
            throw new IllegalStateException("Application run cannot enter draining state");
        }
    }

    @Scheduled(fixedDelayString = "${app.runtime.heartbeat-interval:5s}")
    public void heartbeat() {
        String current = runId;
        if (current == null) {
            return;
        }
        try {
            if (appRunMapper.heartbeat(current) != 1) {
                runId = null;
                fenceOrThrow("Application run heartbeat ownership was lost", null);
            }
        } catch (RuntimeException exception) {
            runId = null;
            fenceOrThrow("Application run heartbeat could not be renewed", exception);
        }
    }

    private void fenceOrThrow(String reason, RuntimeException cause) {
        if (safetyCoordinator != null) {
            safetyCoordinator.fence(reason, cause);
            return;
        }
        throw new IllegalStateException(reason, cause);
    }

    public String currentRunId() {
        return runId;
    }

    public int markStaleRunsCrashed(Duration staleTimeout, int limit) {
        String current = runId;
        if (current == null) {
            return 0;
        }
        return appRunMapper.markStaleActiveRunsCrashed(current, micros(staleTimeout), limit);
    }

    @Override
    public void destroy() {
        String current = runId;
        runId = null;
        if (current != null && appRunMapper.markStopped(current) != 1) {
            throw new IllegalStateException("Application run cannot enter stopped state");
        }
    }

    private boolean persistent() {
        return appProperties.getRuntime().getMode().isPersistent();
    }

    private String applicationVersion() {
        return RuntimeRunRegistry.class.getPackage().getImplementationVersion();
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private String instanceName(String hostName, long processId) {
        String configured = appProperties.getRuntime().getInstanceName();
        return configured == null || configured.isBlank() ? hostName + ":" + processId : configured;
    }

    private String roles() {
        return appProperties.getRuntime().getRoles().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private String joinIds(java.util.List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private long micros(Duration duration) {
        try {
            return Math.multiplyExact(duration.toMillis(), 1_000L);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Application run timeout is too large", exception);
        }
    }
}
