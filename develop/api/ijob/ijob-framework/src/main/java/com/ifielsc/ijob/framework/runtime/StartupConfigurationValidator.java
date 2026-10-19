package com.ifielsc.ijob.framework.runtime;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.AuthMode;
import com.ifielsc.ijob.common.config.AppProperties.PlatformDatabaseVendor;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.regex.Pattern;

@Component
public class StartupConfigurationValidator implements InitializingBean {
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final Pattern POSTGRESQL_SCHEMA =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private final AppProperties appProperties;
    private final boolean flywayEnabled;

    public StartupConfigurationValidator(
            AppProperties appProperties,
            @Value("${spring.flyway.enabled:false}") boolean flywayEnabled) {
        this.appProperties = appProperties;
        this.flywayEnabled = flywayEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        RuntimeMode runtimeMode = appProperties.getRuntime().getMode();
        if (runtimeMode == null) {
            throw new IllegalStateException("Application runtime mode is required");
        }
        PlatformDatabaseVendor databaseVendor = appProperties.getPlatformDatabase().getVendor();
        if (databaseVendor == null) {
            throw new IllegalStateException("Platform database vendor is required");
        }
        if (databaseVendor == PlatformDatabaseVendor.POSTGRESQL
                && (appProperties.getPlatformDatabase().getSchema() == null
                        || !POSTGRESQL_SCHEMA
                                .matcher(appProperties.getPlatformDatabase().getSchema())
                                .matches())) {
            throw new IllegalStateException("PostgreSQL platform schema is invalid");
        }
        if (runtimeMode == RuntimeMode.CLUSTER && databaseVendor == PlatformDatabaseVendor.SQLITE) {
            throw new IllegalStateException("SQLite does not support cluster runtime mode");
        }

        AppProperties.Auth auth = appProperties.getAuth();
        AuthMode authMode = auth.getMode();
        if (runtimeMode == RuntimeMode.STANDALONE
                && authMode != AuthMode.STATIC
                && authMode != AuthMode.DATABASE) {
            throw new IllegalStateException("Standalone mode requires static authentication");
        }
        if (runtimeMode == RuntimeMode.CLUSTER
                && authMode != AuthMode.STATIC
                && authMode != AuthMode.DATABASE) {
            throw new IllegalStateException("Cluster mode requires static authentication");
        }
        if (runtimeMode == RuntimeMode.STANDALONE && !flywayEnabled) {
            throw new IllegalStateException("Standalone mode requires Flyway validation");
        }
        if (runtimeMode == RuntimeMode.CLUSTER && !flywayEnabled) {
            throw new IllegalStateException("Cluster mode requires Flyway validation");
        }
        AppProperties.Runtime runtime = appProperties.getRuntime();
        if (runtimeMode.isClustered()
                && (isBlank(runtime.getInstanceName())
                        || !runtime.getMaintenance().isEnabled()
                        || !appProperties.getNotification().getOutbox().isEnabled())) {
            throw new IllegalStateException(
                    "Cluster mode requires an instance name and all persistent coordinators");
        }
        if ((runtime.getInstanceName() != null && runtime.getInstanceName().length() > 128)
                || runtime.getRoles() == null
                || runtime.getRoles().isEmpty()
                || runtime.getSingletonLockName() == null
                || runtime.getSingletonLockName().isBlank()
                || runtime.getSingletonLockName().length() > 64
                || runtime.getHeartbeatInterval() == null
                || runtime.getHeartbeatInterval().isNegative()
                || runtime.getHeartbeatInterval().isZero()
                || runtime.getIdSegmentSize() < 100
                || runtime.getIdSegmentSize() > 100000) {
            throw new IllegalStateException("Application runtime configuration is invalid");
        }
        if (authMode == AuthMode.STATIC
                || (authMode == AuthMode.DATABASE && auth.isBootstrapEnabled())) {
            validateStaticAuthentication(auth);
        }
        validateScanner(
                "Schedule scanner",
                appProperties.getSchedule().getScanInterval(),
                appProperties.getSchedule().getScanBatchSize());
        validateSchedulePolicy();
        validateScheduleZone();
        validateScheduling(appProperties.getScheduling());
        validateScanner(
                "Event recovery scanner",
                appProperties.getEvent().getRecovery().getScanInterval(),
                appProperties.getEvent().getRecovery().getScanBatchSize());
        validateWorkQueue(appProperties.getRuntime().getWorkQueue());
        validateRuntimeCommand(appProperties.getRuntime().getCommand());
        validateConcurrencyPermit(appProperties.getRuntime().getConcurrencyPermit());
        validateMaintenance(appProperties.getRuntime().getMaintenance());
        validateNotificationOutbox(appProperties.getNotification().getOutbox());
        if (!positive(appProperties.getCacheConsistency().getMaxStaleness())) {
            throw new IllegalStateException("Cache consistency configuration is invalid");
        }
        validateRetention(appProperties.getRetention());
    }

    private void validateStaticAuthentication(AppProperties.Auth auth) {
        if (isBlank(auth.getUsername())
                || isBlank(auth.getPasswordHash())
                || !BCRYPT_HASH.matcher(auth.getPasswordHash()).matches()) {
            throw new IllegalStateException("Static authentication configuration is incomplete");
        }
    }

    private void validateScheduleZone() {
        try {
            ZoneId.of(appProperties.getSchedule().getZoneId());
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalStateException("Schedule business time zone is invalid", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void validateScanner(String name, java.time.Duration interval, int batchSize) {
        if (interval == null
                || interval.isNegative()
                || interval.isZero()
                || batchSize < 1
                || batchSize > 1000) {
            throw new IllegalStateException(name + " configuration is invalid");
        }
    }

    private void validateRetention(AppProperties.Retention retention) {
        if (!positive(retention.getInterval())
                || !positive(retention.getInitialDelay())
                || !positive(retention.getExecution())
                || !positive(retention.getNotification())
                || !positive(retention.getEvent())
                || !positive(retention.getAppRun())
                || !positive(retention.getAudit())
                || retention.getBatchSize() < 1
                || retention.getBatchSize() > 5000
                || retention.getMaxBatchesPerRun() < 1
                || retention.getMaxBatchesPerRun() > 100) {
            throw new IllegalStateException("Runtime data retention configuration is invalid");
        }
    }

    private void validateWorkQueue(AppProperties.WorkQueue workQueue) {
        if (workQueue == null
                || !positive(workQueue.getScanInterval())
                || !positive(workQueue.getMaxIdleScanInterval())
                || workQueue.getMaxIdleScanInterval().compareTo(workQueue.getScanInterval()) < 0
                || workQueue.getScanJitter() == null
                || workQueue.getScanJitter().isNegative()
                || !positive(workQueue.getRenewInterval())
                || !positive(workQueue.getLeaseDuration())
                || !positive(workQueue.getRetryDelay())
                || workQueue.getRenewInterval().compareTo(workQueue.getLeaseDuration()) >= 0
                || workQueue.getScanBatchSize() < 1
                || workQueue.getScanBatchSize() > 1000
                || workQueue.getMaxInflightRoots() < 1
                || workQueue.getMaxInflightRoots() > 10000
                || workQueue.getMaxAttempts() < 1
                || workQueue.getMaxAttempts() > 100) {
            throw new IllegalStateException("Trigger work queue configuration is invalid");
        }
        AppProperties.Pool triggerPool = appProperties.getExecution().getTrigger();
        if (appProperties.getRuntime().getMode().isPersistent()
                && workQueue.getMaxInflightRoots()
                        > triggerPool.getMaxSize() + triggerPool.getQueueCapacity()) {
            throw new IllegalStateException(
                    "max-inflight-roots exceeds trigger executor admission capacity");
        }
    }

    private void validateRuntimeCommand(AppProperties.Command command) {
        if (command == null
                || !positive(command.getScanInterval())
                || !positive(command.getRetryDelay())
                || !positive(command.getTimeout())
                || command.getScanBatchSize() < 1
                || command.getScanBatchSize() > 1000) {
            throw new IllegalStateException("Runtime command configuration is invalid");
        }
    }

    private void validateConcurrencyPermit(AppProperties.ConcurrencyPermit permit) {
        if (permit == null
                || !positive(permit.getWaitTimeout())
                || !positive(permit.getRetryInterval())
                || !positive(permit.getMaxRetryInterval())
                || permit.getMaxRetryInterval().compareTo(permit.getRetryInterval()) < 0
                || permit.getRetryJitter() == null
                || permit.getRetryJitter().isNegative()
                || !positive(permit.getRenewInterval())
                || !positive(permit.getLeaseDuration())
                || permit.getRenewInterval().compareTo(permit.getLeaseDuration()) >= 0) {
            throw new IllegalStateException("Concurrency permit configuration is invalid");
        }
    }

    private void validateSchedulePolicy() {
        AppProperties.Schedule schedule = appProperties.getSchedule();
        if (!positive(schedule.getInitialDelay())
                || schedule.getMaxTriggersPerScan() < 1
                || schedule.getMaxTriggersPerScan() > 10_000
                || schedule.getMisfirePolicy() == null
                || schedule.getMisfireGrace() == null
                || schedule.getMisfireGrace().isNegative()
                || !positive(schedule.getMaxCatchupWindow())
                || schedule.getMaxCatchupCount() < 1
                || schedule.getMaxCatchupCount() > 1_000) {
            throw new IllegalStateException("Schedule misfire configuration is invalid");
        }
    }

    private void validateScheduling(AppProperties.Scheduling scheduling) {
        if (scheduling == null
                || scheduling.getPoolSize() < 2
                || scheduling.getPoolSize() > 64
                || !positive(scheduling.getShutdownTimeout())) {
            throw new IllegalStateException("Scheduling configuration is invalid");
        }
    }

    private void validateNotificationOutbox(AppProperties.Outbox outbox) {
        if (outbox == null
                || !positive(outbox.getScanInterval())
                || !positive(outbox.getRenewInterval())
                || !positive(outbox.getLeaseDuration())
                || !positive(outbox.getRetryDelay())
                || outbox.getRenewInterval().compareTo(outbox.getLeaseDuration()) >= 0
                || outbox.getScanBatchSize() < 1
                || outbox.getScanBatchSize() > 1000
                || outbox.getMaxAttempts() < 1
                || outbox.getMaxAttempts() > 100) {
            throw new IllegalStateException("Notification outbox configuration is invalid");
        }
    }

    private void validateMaintenance(AppProperties.Maintenance maintenance) {
        if (maintenance == null
                || !positive(maintenance.getScanInterval())
                || !positive(maintenance.getRenewInterval())
                || !positive(maintenance.getLeaseDuration())
                || !positive(maintenance.getStaleRunTimeout())
                || !positive(maintenance.getOrphanGrace())
                || maintenance.getRenewInterval().compareTo(maintenance.getLeaseDuration()) >= 0
                || maintenance.getStaleRunTimeout().compareTo(maintenance.getLeaseDuration()) < 0
                || appProperties
                                .getRuntime()
                                .getHeartbeatInterval()
                                .compareTo(maintenance.getStaleRunTimeout())
                        >= 0
                || maintenance.getBatchSize() < 1
                || maintenance.getBatchSize() > 1000
                || maintenance.getMaxBatchesPerRun() < 1
                || maintenance.getMaxBatchesPerRun() > 100) {
            throw new IllegalStateException("Runtime maintenance configuration is invalid");
        }
    }

    private boolean positive(java.time.Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }
}
