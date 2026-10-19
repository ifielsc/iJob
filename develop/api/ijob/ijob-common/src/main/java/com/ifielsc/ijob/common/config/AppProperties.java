package com.ifielsc.ijob.common.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {
    private PlatformDatabase platformDatabase = new PlatformDatabase();
    private Runtime runtime = new Runtime();
    private Auth auth = new Auth();
    private Security security = new Security();
    private Schedule schedule = new Schedule();
    private Event event = new Event();
    private Retention retention = new Retention();
    private Http http = new Http();
    private Execution execution = new Execution();
    private Notification notification = new Notification();
    private CacheConsistency cacheConsistency = new CacheConsistency();
    private Scheduling scheduling = new Scheduling();

    public enum PlatformDatabaseVendor {
        MYSQL,
        POSTGRESQL,
        SQLSERVER,
        ORACLE,
        SQLITE
    }

    @Data
    public static class PlatformDatabase {
        private PlatformDatabaseVendor vendor = PlatformDatabaseVendor.MYSQL;
        private String schema;
    }

    public enum NotificationLocale {
        EN,
        ZH
    }

    @Data
    public static class Notification {
        private NotificationLocale locale = NotificationLocale.EN;
        private Outbox outbox = new Outbox();
    }

    @Data
    public static class Outbox {
        private boolean enabled = true;
        private Duration scanInterval = Duration.ofSeconds(1);
        private Duration renewInterval = Duration.ofSeconds(10);
        private Duration leaseDuration = Duration.ofSeconds(30);
        private Duration retryDelay = Duration.ofSeconds(30);
        private int scanBatchSize = 100;
        private int maxAttempts = 5;
    }

    @Data
    public static class CacheConsistency {
        private Duration maxStaleness = Duration.ofSeconds(5);
    }

    public enum RuntimeMode {
        DEVELOPMENT,
        STANDALONE,
        CLUSTER;

        public boolean isPersistent() {
            return this == STANDALONE || this == CLUSTER;
        }

        public boolean isClustered() {
            return this == CLUSTER;
        }
    }

    public enum RuntimeRole {
        API,
        SCHEDULER,
        WORKER,
        MAINTENANCE
    }

    public enum AuthMode {
        NONE,
        STATIC,
        DATABASE
    }

    @Data
    public static class Runtime {
        private RuntimeMode mode = RuntimeMode.DEVELOPMENT;
        private String instanceName;
        private Set<RuntimeRole> roles = EnumSet.allOf(RuntimeRole.class);
        private List<String> groups = new ArrayList<>();
        private List<Long> allowedTaskIds = new ArrayList<>();
        private List<Long> allowedJobIds = new ArrayList<>();
        private String singletonLockName = "ijob:standalone";
        private Duration heartbeatInterval = Duration.ofSeconds(5);

        /** Runtime/log namespaces only; management uses 1 and DAG detail uses 100. */
        private int idSegmentSize = 1000;

        private WorkQueue workQueue = new WorkQueue();
        private Command command = new Command();
        private ConcurrencyPermit concurrencyPermit = new ConcurrencyPermit();
        private Maintenance maintenance = new Maintenance();
    }

    @Data
    public static class WorkQueue {
        private Duration scanInterval = Duration.ofSeconds(1);
        private Duration maxIdleScanInterval = Duration.ofSeconds(5);
        private Duration scanJitter = Duration.ofMillis(200);
        private Duration renewInterval = Duration.ofSeconds(5);
        private int scanBatchSize = 100;
        private int maxInflightRoots = 20;
        private Duration leaseDuration = Duration.ofSeconds(30);
        private Duration retryDelay = Duration.ofSeconds(5);
        private int maxAttempts = 5;
    }

    @Data
    public static class Command {
        private Duration scanInterval = Duration.ofMillis(250);
        private int scanBatchSize = 100;
        private Duration retryDelay = Duration.ofMillis(250);
        private Duration timeout = Duration.ofMinutes(5);
    }

    @Data
    public static class ConcurrencyPermit {
        private Duration waitTimeout = Duration.ofMinutes(5);
        private Duration retryInterval = Duration.ofMillis(100);
        private Duration maxRetryInterval = Duration.ofSeconds(2);
        private Duration retryJitter = Duration.ofMillis(100);
        private Duration renewInterval = Duration.ofSeconds(5);
        private Duration leaseDuration = Duration.ofSeconds(30);
    }

    @Data
    public static class Maintenance {
        private boolean enabled = true;
        private Duration scanInterval = Duration.ofSeconds(5);
        private Duration renewInterval = Duration.ofSeconds(10);
        private Duration leaseDuration = Duration.ofSeconds(30);
        private Duration staleRunTimeout = Duration.ofSeconds(30);
        private Duration orphanGrace = Duration.ofMinutes(1);
        private int batchSize = 100;
        private int maxBatchesPerRun = 10;
    }

    @Data
    public static class Security {
        private Aes aes = new Aes();

        public static class Aes {
            private String key;
            private String keyId = "local";

            public String getKey() {
                return key;
            }

            public void setKey(String key) {
                this.key = key;
            }

            public String getKeyId() {
                return keyId;
            }

            public void setKeyId(String keyId) {
                this.keyId = keyId;
            }

            @Override
            public String toString() {
                return "Aes{key=<redacted>, keyId='" + keyId + "'}";
            }
        }
    }

    public static class Auth {
        private AuthMode mode = AuthMode.NONE;
        private String username;
        private String passwordHash;
        private boolean bootstrapEnabled;

        public boolean isBootstrapEnabled() {
            return bootstrapEnabled;
        }

        public void setBootstrapEnabled(boolean bootstrapEnabled) {
            this.bootstrapEnabled = bootstrapEnabled;
        }

        public AuthMode getMode() {
            return mode;
        }

        public void setMode(AuthMode mode) {
            this.mode = mode;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        @Override
        public String toString() {
            return "Auth{mode=" + mode + ", username='" + username + "', passwordHash=<redacted>}";
        }
    }

    @Data
    public static class Schedule {
        private boolean enabled;
        private Duration scanInterval = Duration.ofSeconds(1);
        private Duration initialDelay = Duration.ofSeconds(5);
        private int scanBatchSize = 100;
        private int maxTriggersPerScan = 100;
        private String zoneId = "Asia/Shanghai";
        private ScheduleMisfirePolicy misfirePolicy = ScheduleMisfirePolicy.FIRE_ONCE;
        private Duration misfireGrace = Duration.ofSeconds(60);
        private Duration maxCatchupWindow = Duration.ofHours(24);
        private int maxCatchupCount = 10;
    }

    public enum ScheduleMisfirePolicy {
        SKIP,
        FIRE_ONCE,
        CATCH_UP_BOUNDED
    }

    @Data
    public static class Scheduling {
        private int poolSize = 8;
        private Duration shutdownTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class Event {
        private Recovery recovery = new Recovery();

        @Data
        public static class Recovery {
            private boolean enabled = true;
            private Duration scanInterval = Duration.ofSeconds(1);
            private int scanBatchSize = 100;
        }
    }

    @Data
    public static class Retention {
        private boolean enabled;
        private boolean dryRun = true;
        private Duration interval = Duration.ofHours(1);
        private Duration initialDelay = Duration.ofMinutes(1);
        private int batchSize = 500;
        private int maxBatchesPerRun = 10;
        private Duration execution = Duration.ofDays(90);
        private Duration notification = Duration.ofDays(90);
        private Duration event = Duration.ofDays(180);
        private Duration appRun = Duration.ofDays(30);
        private Duration audit = Duration.ofDays(90);
    }

    @Data
    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(30);
    }

    @Data
    public static class Execution {
        private Duration shutdownTimeout = Duration.ofSeconds(30);
        private Pool trigger = new Pool(4, 20, 0, Duration.ofSeconds(60));
        private Pool job = new Pool(4, 20, 0, Duration.ofSeconds(60));
        private Pool dag = new Pool(8, 32, 500, Duration.ofSeconds(60));
        private Pool task = new Pool(8, 32, 1000, Duration.ofSeconds(60));
        private Pool script = new Pool(4, 16, 0, Duration.ofSeconds(60));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pool {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;
        private Duration keepAlive;
    }
}
