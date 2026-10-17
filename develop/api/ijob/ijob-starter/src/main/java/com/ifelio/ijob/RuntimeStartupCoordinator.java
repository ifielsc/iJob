package com.ifelio.ijob;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.config.AppProperties.RuntimeMode;
import com.ifelio.ijob.common.routing.ExecutionGroupCodes;
import com.ifelio.ijob.common.routing.ExecutionTargetIds;
import com.ifelio.ijob.common.runtime.ApplicationRuntimeState;
import com.ifelio.ijob.core.maintenance.MaintenanceLeaderCoordinator;
import com.ifelio.ijob.framework.runtime.RuntimeRunRegistry;
import com.ifelio.ijob.framework.runtime.SingletonDatabaseLock;
import com.ifelio.ijob.infra.mapper.job.JobMapper;
import com.ifelio.ijob.infra.mapper.task.TaskMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RuntimeStartupCoordinator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeStartupCoordinator.class);

    private final AppProperties appProperties;
    private final ApplicationRuntimeState runtimeState;
    private final SingletonDatabaseLock singletonDatabaseLock;
    private final RuntimeRunRegistry runtimeRunRegistry;
    private final MaintenanceLeaderCoordinator maintenanceLeaderCoordinator;
    private final DatabaseSchemaVersionGuard schemaVersionGuard;
    private final TaskMapper taskMapper;
    private final JobMapper jobMapper;
    private final com.ifelio.ijob.core.auth.AccountService accountService;

    public RuntimeStartupCoordinator(
            AppProperties appProperties,
            ApplicationRuntimeState runtimeState,
            SingletonDatabaseLock singletonDatabaseLock,
            RuntimeRunRegistry runtimeRunRegistry,
            MaintenanceLeaderCoordinator maintenanceLeaderCoordinator,
            DatabaseSchemaVersionGuard schemaVersionGuard,
            TaskMapper taskMapper,
            JobMapper jobMapper,
            com.ifelio.ijob.core.auth.AccountService accountService) {
        this.appProperties = appProperties;
        this.runtimeState = runtimeState;
        this.singletonDatabaseLock = singletonDatabaseLock;
        this.runtimeRunRegistry = runtimeRunRegistry;
        this.maintenanceLeaderCoordinator = maintenanceLeaderCoordinator;
        this.schemaVersionGuard = schemaVersionGuard;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        appProperties
                .getRuntime()
                .setGroups(ExecutionGroupCodes.normalize(appProperties.getRuntime().getGroups()));
        appProperties
                .getRuntime()
                .setAllowedTaskIds(
                        ExecutionTargetIds.normalize(
                                appProperties.getRuntime().getAllowedTaskIds(), "Task"));
        appProperties
                .getRuntime()
                .setAllowedJobIds(
                        ExecutionTargetIds.normalize(
                                appProperties.getRuntime().getAllowedJobIds(), "Job"));
        if (appProperties.getRuntime().getMode().isPersistent()) {
            schemaVersionGuard.verify();
            accountService.initialize();
            warnAboutMissingExecutionTargets();
            if (appProperties.getRuntime().getMode() == RuntimeMode.STANDALONE) {
                singletonDatabaseLock.acquire();
            }
            runtimeRunRegistry.begin();
            runtimeRunRegistry.markRunning();
            maintenanceLeaderCoordinator.start();
        } else if (appProperties.getAuth().getMode() == AppProperties.AuthMode.DATABASE) {
            schemaVersionGuard.verify();
            accountService.initialize();
        }
        runtimeState.markReady();
    }

    private void warnAboutMissingExecutionTargets() {
        var missingTaskIds =
                appProperties.getRuntime().getAllowedTaskIds().stream()
                        .filter(id -> taskMapper.selectById(id) == null)
                        .toList();
        if (!missingTaskIds.isEmpty()) {
            LOGGER.warn(
                    "Configured Worker Task IDs do not currently resolve to active published tasks:"
                            + " {}",
                    missingTaskIds);
        }

        var missingJobIds =
                appProperties.getRuntime().getAllowedJobIds().stream()
                        .filter(id -> jobMapper.selectById(id) == null)
                        .toList();
        if (!missingJobIds.isEmpty()) {
            LOGGER.warn(
                    "Configured Worker Job IDs do not currently resolve to active published jobs:"
                            + " {}",
                    missingJobIds);
        }
    }
}
