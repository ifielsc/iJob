package com.ifielsc.ijob.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeMode;
import com.ifielsc.ijob.common.config.AppProperties.RuntimeRole;
import com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper;
import com.ifielsc.ijob.infra.mapper.runtime.AppRunMapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

class RuntimeRunRegistryTest {
    private static final LocalDateTime DATABASE_TIME =
            LocalDateTime.of(2026, 9, 9, 10, 30, 45, 123_000_000);

    @Test
    void standaloneLifecycleShouldUseDatabaseTimeAndPersistIdentity() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.STANDALONE);
        properties.getRuntime().setInstanceName("node-a");
        properties.getRuntime().setRoles(EnumSet.of(RuntimeRole.API, RuntimeRole.WORKER));
        properties.getRuntime().setGroups(List.of("cpu", "gpu"));
        properties.getRuntime().setAllowedTaskIds(List.of(11L, 12L));
        properties.getRuntime().setAllowedJobIds(List.of(21L));
        RecordingMapper mapper = new RecordingMapper();
        DatabaseClockMapper clock = () -> DATABASE_TIME;
        RuntimeRunRegistry registry = new RuntimeRunRegistry(mapper, clock, properties);

        registry.begin();
        assertNotNull(registry.currentRunId());
        assertEquals(0, mapper.markCrashedCalls);
        assertEquals("node-a", mapper.instanceName);
        assertEquals("STANDALONE", mapper.runtimeMode);
        assertEquals("API,WORKER", mapper.roles);
        assertEquals("cpu,gpu", mapper.workerGroups);
        assertEquals("11,12", mapper.workerTaskIds);
        assertEquals("21", mapper.workerJobIds);
        assertEquals(10, mapper.runtimeProtocolVersion);
        assertEquals(DATABASE_TIME, mapper.startedAt);

        registry.markRunning();
        assertEquals(1, registry.markStaleRunsCrashed(Duration.ofSeconds(30), 100));
        registry.heartbeat();
        registry.markDraining();
        registry.destroy();

        assertEquals(1, mapper.runningCalls);
        assertEquals(1, mapper.heartbeatCalls);
        assertEquals(1, mapper.drainingCalls);
        assertEquals(1, mapper.stoppedCalls);
        assertNull(registry.currentRunId());
    }

    @Test
    void developmentLifecycleShouldNotWriteRegistry() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.DEVELOPMENT);
        RecordingMapper mapper = new RecordingMapper();
        RuntimeRunRegistry registry =
                new RuntimeRunRegistry(mapper, () -> DATABASE_TIME, properties);

        registry.begin();
        registry.heartbeat();
        registry.destroy();

        assertEquals(0, mapper.markCrashedCalls);
        assertEquals(0, mapper.insertCalls);
        assertNull(registry.currentRunId());
    }

    @Test
    void clusterLifecycleShouldPersistTheClusterIdentity() {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setMode(RuntimeMode.CLUSTER);
        properties.getRuntime().setInstanceName("cluster-node-b");
        RecordingMapper mapper = new RecordingMapper();
        RuntimeRunRegistry registry =
                new RuntimeRunRegistry(mapper, () -> DATABASE_TIME, properties);

        registry.begin();

        assertEquals(1, mapper.insertCalls);
        assertEquals("CLUSTER", mapper.runtimeMode);
        assertEquals("cluster-node-b", mapper.instanceName);
        registry.destroy();
    }

    private static final class RecordingMapper implements AppRunMapper {
        private int markCrashedCalls;
        private int insertCalls;
        private int runningCalls;
        private int heartbeatCalls;
        private int drainingCalls;
        private int stoppedCalls;
        private String instanceName;
        private String runtimeMode;
        private String roles;
        private String workerGroups;
        private String workerTaskIds;
        private String workerJobIds;
        private int runtimeProtocolVersion;
        private LocalDateTime startedAt;

        @Override
        public int markStaleActiveRunsCrashed(String excludeRunId, long staleMicros, int limit) {
            markCrashedCalls++;
            assertNotNull(excludeRunId);
            assertEquals(30_000_000L, staleMicros);
            assertEquals(100, limit);
            return 1;
        }

        @Override
        public int insertStarting(
                String runId,
                String instanceName,
                String runtimeMode,
                String roles,
                String workerGroups,
                String workerTaskIds,
                String workerJobIds,
                int runtimeProtocolVersion,
                String applicationVersion,
                String hostName,
                Long processId,
                LocalDateTime startedAt) {
            insertCalls++;
            this.instanceName = instanceName;
            this.runtimeMode = runtimeMode;
            this.roles = roles;
            this.workerGroups = workerGroups;
            this.workerTaskIds = workerTaskIds;
            this.workerJobIds = workerJobIds;
            this.runtimeProtocolVersion = runtimeProtocolVersion;
            this.startedAt = startedAt;
            return 1;
        }

        @Override
        public int markRunning(String runId) {
            runningCalls++;
            return 1;
        }

        @Override
        public int heartbeat(String runId) {
            heartbeatCalls++;
            return 1;
        }

        @Override
        public int markDraining(String runId) {
            drainingCalls++;
            return 1;
        }

        @Override
        public int markStopped(String runId) {
            stoppedCalls++;
            return 1;
        }
    }
}
