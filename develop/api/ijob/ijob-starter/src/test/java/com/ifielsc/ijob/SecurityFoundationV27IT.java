package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.auth.AccountRole;
import com.ifielsc.ijob.infra.coordination.RuntimeMutationGuard;
import com.ifielsc.ijob.infra.entity.auth.AuthUser;
import com.ifielsc.ijob.infra.entity.auth.OperationAudit;
import com.ifielsc.ijob.infra.mapper.auth.AuthUserMapper;
import com.ifielsc.ijob.infra.mapper.auth.OperationAuditMapper;
import com.ifielsc.ijob.infra.mapper.coordination.RuntimeGuardMapper;
import com.ifielsc.ijob.infra.mybatis.AutoIdInterceptor;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in real-database persistence tests. Creates only a new isolated target; never cleans it. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityFoundationV27IT {
    private String targetPrefix = "s27";

    void targetPrefix(String targetPrefix) {
        this.targetPrefix = targetPrefix;
    }

    DriverManagerDataSource dataSource() {
        return dataSource;
    }

    SqlSessionTemplate sessions() {
        return sessions;
    }

    RuntimeMutationGuard guard() {
        return guard;
    }

    private String vendor;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sessions;
    private TransactionTemplate transaction;
    private RuntimeMutationGuard guard;
    private final AtomicLong ids = new AtomicLong(900000);
    private final java.util.concurrent.atomic.AtomicBoolean failNextConnection =
            new java.util.concurrent.atomic.AtomicBoolean();

    @BeforeAll
    void provisionAndUpgrade() throws Exception {
        vendor = System.getenv("IJOB_AUDIT_VENDOR");
        Assumptions.assumeTrue(vendor != null, "Use tools/run-security-v27-audit.ps1");
        String run = System.getenv("IJOB_AUDIT_RUN_ID");
        if (run == null || !run.matches("[0-9]{12}"))
            throw new IllegalArgumentException("Invalid run ID");
        String name = "ijob_it_" + targetPrefix + "_" + run;
        String adminPassword = System.getenv("IJOB_AUDIT_ADMIN_PASSWORD");
        String user;
        String password;
        String url;
        if (vendor.equals("sqlite")) {
            url =
                    "jdbc:sqlite:"
                            + Files.createTempDirectory("ijob-v27-" + run).resolve("audit.db")
                            // Match database/sqlite.yml: SQLite serializes writers at BEGIN,
                            // rather than emulating row-level SELECT FOR UPDATE.
                            + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE";
            user = "";
            password = "";
        } else if (vendor.equals("oracle")) {
            url = "jdbc:oracle:thin:@//127.0.0.1:1521/ORCL";
            user = name.toUpperCase(java.util.Locale.ROOT);
            password = System.getenv("IJOB_AUDIT_TEST_PASSWORD");
            try (Connection admin =
                            DriverManager.getConnection(url, "sys as sysdba", adminPassword);
                    Statement statement = admin.createStatement()) {
                // Do not propagate SQL text containing the temporary user's credential.
                try {
                    statement.executeUpdate(
                            "CREATE USER "
                                    + user
                                    + " IDENTIFIED BY \""
                                    + password.replace("\"", "\"\"")
                                    + "\" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS");
                } catch (SQLException exception) {
                    throw new IllegalStateException(
                            "Isolated Oracle user creation failed; SQL code "
                                    + exception.getErrorCode());
                }
                statement.executeUpdate(
                        "GRANT CREATE SESSION, CREATE TABLE, CREATE TRIGGER, CREATE VIEW TO "
                                + user);
            }
        } else {
            String adminUrl;
            switch (vendor) {
                case "mysql" -> {
                    user = "root";
                    adminUrl =
                            "jdbc:mysql://127.0.0.1:3306/mysql?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
                    url = adminUrl.replace("/mysql?", "/" + name + "?");
                }
                case "postgresql" -> {
                    user = "postgres";
                    adminUrl = "jdbc:postgresql://127.0.0.1:5432/postgres";
                    url = "jdbc:postgresql://127.0.0.1:5432/" + name;
                }
                case "sqlserver" -> {
                    user = "sa";
                    adminUrl =
                            "jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true";
                    url = adminUrl.replace("databaseName=master", "databaseName=" + name);
                }
                default -> throw new IllegalArgumentException("Unsupported vendor");
            }
            password = adminPassword;
            try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
                    Statement statement = admin.createStatement()) {
                statement.executeUpdate(
                        "CREATE DATABASE "
                                + name
                                + (vendor.equals("mysql") ? " CHARACTER SET utf8mb4" : ""));
            }
        }
        dataSource =
                new DriverManagerDataSource(url, user, password) {
                    @Override
                    public Connection getConnection() throws SQLException {
                        if (failNextConnection.getAndSet(false))
                            throw new SQLException("Injected transient connection outage", "08006");
                        return super.getConnection();
                    }
                };
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            // Deliberately omit URL, username and properties from evidence.
            System.out.printf(
                    "DATABASE_EVIDENCE vendor=%s product=%s version=%s driver=%s driverVersion=%s"
                            + " isolation=%d%n",
                    vendor,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion().replaceAll("[\\r\\n]+", " "),
                    metadata.getDriverName(),
                    metadata.getDriverVersion(),
                    connection.getTransactionIsolation());
        }
        // Verify the target before any migration or fixture writes.
        if (!vendor.equals("sqlite")) {
            String actual =
                    jdbc.queryForObject(
                            switch (vendor) {
                                case "mysql" -> "SELECT DATABASE()";
                                case "postgresql" -> "SELECT current_database()";
                                case "sqlserver" -> "SELECT DB_NAME()";
                                default -> "SELECT USER FROM dual";
                            },
                            String.class);
            assertEquals(name.toLowerCase(), actual.toLowerCase());
        }
        flyway("26").migrate();
        // A pre-existing row must gain a usable revision during upgrade.
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES"
                        + " (1,'audit','EVENT','{}')");
        jdbc.update(
                "INSERT INTO ifj_schedule"
                    + " (id,target_type,target_id,schedule_type,interval_sec,next_fire_time) VALUES"
                    + " (1,'TASK',1,'INTERVAL',60,?)",
                time(LocalDateTime.of(2020, 1, 1, 0, 0)));
        assertEquals(vendor.equals("sqlite") ? 11 : 12, flyway(null).migrate().migrationsExecuted);
        flyway(null).validate();
        if (vendor.equals("mysql")) {
            assertEquals(
                    "InnoDB",
                    jdbc.queryForObject(
                            "SELECT ENGINE FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE() AND table_name='ifj_runtime_guard'",
                            String.class));
        }
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        assertEquals(
                38,
                jdbc.queryForObject(
                        "SELECT capability_version FROM ifj_schema_capability WHERE"
                                + " capability_key='platform-schema'",
                        Integer.class));
        assertEquals(
                0L,
                jdbc.queryForObject("SELECT revision FROM ifj_schedule WHERE id=1", Long.class));
        // The historical upgrade must preserve auth semantics, while release startup rejects it.
        assertThrows(
                IllegalStateException.class,
                () -> {
                    switch (vendor) {
                        case "mysql" -> new MySqlSchemaCompatibilityInspector(dataSource).verify();
                        case "postgresql" ->
                                new PostgreSqlSchemaCompatibilityInspector(dataSource).verify();
                        case "sqlserver" ->
                                new SqlServerSchemaCompatibilityInspector(dataSource).verify();
                        case "oracle" ->
                                new OracleSchemaCompatibilityInspector(dataSource).verify();
                        default -> new SqliteSchemaCompatibilityInspector(dataSource).verify();
                    }
                });
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        if (vendor.equals("oracle"))
            configuration.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
        if (vendor.equals("sqlite"))
            configuration
                    .getTypeHandlerRegistry()
                    .register(
                            LocalDateTime.class,
                            new com.ifielsc.ijob.framework.database.typehandler
                                    .SqliteLocalDateTimeTypeHandler());
        configuration.addInterceptor(new AutoIdInterceptor(namespace -> ids.incrementAndGet()));
        var pagination = new com.github.pagehelper.PageInterceptor();
        var paginationProperties = new java.util.Properties();
        paginationProperties.setProperty(
                "helperDialect", vendor.equals("sqlserver") ? "sqlserver2012" : vendor);
        pagination.setProperties(paginationProperties);
        configuration.addInterceptor(pagination);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml"));
        sessions = new SqlSessionTemplate(factory.getObject());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        guard = new RuntimeMutationGuard(sessions.getMapper(RuntimeGuardMapper.class));
        assertEquals(0L, users().countAll());
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_operation_audit", Integer.class));
        System.out.println("V27 isolated target verified: " + vendor + " / " + name);
    }

    private Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration/" + vendor)
                        .table("ifj_schema_history")
                        .cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    @Test
    void accountIdentityAndUnicodeRoundTripWithoutHashInDiagnostics() {
        AuthUser user = user("管理员", AccountRole.ADMIN);
        user.setLoginId("legacy-admin-" + user.getId());
        insert(user);
        AuthUser loaded = users().selectByLoginId(user.getLoginId());
        assertEquals(user.getId(), loaded.getId());
        assertEquals(user.getUsername(), loaded.getUsername());
        assertEquals(user.getDisplayName(), loaded.getDisplayName());
        assertEquals(user.getPasswordHash(), loaded.getPasswordHash());
        assertEquals(AccountRole.ADMIN, loaded.getRoleCode());
        assertTrue(loaded.getEnabled());
        assertTrue(loaded.getMustChangePassword());
        assertFalse(loaded.toString().contains(user.getPasswordHash()));
    }

    @Test
    void duplicateUsernamesAndPrincipalIdsAreRejected() {
        AuthUser first = user("unique", AccountRole.VIEWER);
        insert(first);
        AuthUser duplicateName = user("other", AccountRole.VIEWER);
        duplicateName.setUsername(first.getUsername());
        assertThrows(RuntimeException.class, () -> insert(duplicateName));
        AuthUser duplicateId = user("other", AccountRole.VIEWER);
        duplicateId.setLoginId(first.getLoginId());
        assertThrows(RuntimeException.class, () -> insert(duplicateId));
        assertNotNull(users().selectById(first.getId()));
    }

    @Test
    void usernameLookupIsCaseSensitiveAcrossVendors() {
        AuthUser first = user("CaseSensitive", AccountRole.VIEWER);
        insert(first);
        assertNull(
                users().selectByUsername(first.getUsername().toLowerCase(java.util.Locale.ROOT)));
        assertNotNull(users().selectByUsername(first.getUsername()));
    }

    @Test
    void databaseDefaultsAreViewerEnabledAndMustChangePassword() {
        long id = ids.incrementAndGet();
        jdbc.update(
                "INSERT INTO ifj_auth_user"
                        + " (id,login_id,username,password_hash,created_by,create_time,update_time)"
                        + " VALUES (?,?,?,?,?,?,?)",
                id,
                "user:" + id,
                "defaults-" + id,
                "synthetic-hash",
                "SYSTEM",
                time(now()),
                time(now()));
        AuthUser loaded = users().selectById(id);
        assertEquals(AccountRole.VIEWER, loaded.getRoleCode());
        assertTrue(loaded.getEnabled());
        assertTrue(loaded.getMustChangePassword());
        assertEquals(0L, loaded.getSecurityVersion());
        assertEquals(0L, loaded.getRevision());
    }

    @Test
    void profileUpdateCannotMutateIdentityPasswordOrAccess() {
        AuthUser user = user("profile", AccountRole.VIEWER);
        insert(user);
        String originalPrincipal = user.getLoginId();
        String originalName = user.getUsername();
        String originalHash = user.getPasswordHash();
        user.setDisplayName("新的显示名");
        user.setLoginId("malicious-replacement");
        user.setUsername("renamed");
        user.setPasswordHash("unexpected-password");
        user.setRoleCode(AccountRole.ADMIN);
        assertEquals(1, transaction.<Integer>execute(status -> users().updateProfile(user, 0)));
        AuthUser loaded = users().selectById(user.getId());
        assertEquals(originalPrincipal, loaded.getLoginId());
        assertEquals(originalName, loaded.getUsername());
        assertEquals(originalHash, loaded.getPasswordHash());
        assertEquals(AccountRole.VIEWER, loaded.getRoleCode());
        assertEquals("新的显示名", loaded.getDisplayName());
        assertEquals(1L, loaded.getRevision());
        assertEquals(0L, loaded.getSecurityVersion());
        assertEquals(0, transaction.<Integer>execute(status -> users().updateProfile(user, 0)));
    }

    @Test
    void accessAndPasswordWritesAdvanceSecurityVersionAndRejectStaleRevision() {
        AuthUser user = user("version", AccountRole.OPERATOR);
        insert(user);
        user.setEnabled(false);
        assertEquals(1, transaction.<Integer>execute(status -> users().updateAccess(user, 0)));
        assertEquals(0, transaction.<Integer>execute(status -> users().updateAccess(user, 0)));
        user.setPasswordHash("synthetic-replacement-hash");
        user.setMustChangePassword(false);
        assertEquals(1, transaction.<Integer>execute(status -> users().updatePassword(user, 1)));
        AuthUser loaded = users().selectById(user.getId());
        assertEquals(2L, loaded.getSecurityVersion());
        assertEquals(2L, loaded.getRevision());
        assertFalse(loaded.getEnabled());
        assertFalse(loaded.getMustChangePassword());
        assertEquals(user.getPasswordHash(), loaded.getPasswordHash());
    }

    @Test
    void administratorCountExcludesDisabledAccounts() {
        long before = users().countEnabledAdministrators();
        AuthUser first = user("admin-enabled", AccountRole.ADMIN);
        AuthUser second = user("admin-disabled", AccountRole.ADMIN);
        second.setEnabled(false);
        insert(first);
        insert(second);
        assertEquals(before + 1, users().countEnabledAdministrators());
    }

    @Test
    void auditRoundTripsCorrelationWithoutRequestBody() {
        OperationAudit audit = audit(ids.incrementAndGet());
        transaction.executeWithoutResult(status -> assertEquals(1, audits().insert(audit)));
        OperationAudit loaded = audits().selectById(audit.getId());
        assertEquals(audit.getActorLoginId(), loaded.getActorLoginId());
        assertEquals(audit.getActorName(), loaded.getActorName());
        assertEquals("ACCOUNT_CREATED", loaded.getActionCode());
        assertEquals("roleCode,enabled", loaded.getChangedFields());
        assertEquals("0123456789abcdef", loaded.getTraceId());
        assertEquals(audit.getRequestId(), loaded.getRequestId());
        assertEquals(audit.getOccurredAt(), loaded.getOccurredAt());
    }

    @Test
    void businessFailureRollsBackAccountAndAuditTogether() {
        AuthUser user = user("rollback", AccountRole.VIEWER);
        OperationAudit audit = audit(user.getId());
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                                    assertEquals(1, users().insert(user));
                                    assertEquals(1, audits().insert(audit));
                                    throw new IllegalStateException("Injected business failure");
                                }));
        assertNull(users().selectById(user.getId()));
        assertNull(audits().selectById(audit.getId()));
    }

    @Test
    void auditConstraintFailureRollsBackAccountWrite() {
        AuthUser user = user("audit-failure", AccountRole.VIEWER);
        OperationAudit audit = audit(user.getId());
        audit.setActionCode(null);
        assertThrows(
                RuntimeException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    assertEquals(1, users().insert(user));
                                    audits().insert(audit);
                                }));
        assertNull(users().selectById(user.getId()));
    }

    @Test
    void guardSerializesConcurrentTransactionsAndRollsBackRevision() throws Exception {
        long before =
                sessions.getMapper(RuntimeGuardMapper.class)
                        .selectRevision(RuntimeMutationGuard.AUTH_ACCOUNTS);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = new ArrayList<Callable<Long>>();
            for (int index = 0; index < 8; index++) {
                calls.add(
                        () ->
                                transaction.execute(
                                        status -> guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS)));
            }
            var revisions = new HashSet<Long>();
            for (var future : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) {
                revisions.add(future.get());
            }
            assertEquals(8, revisions.size());
        }
        assertEquals(
                before + 8,
                sessions.getMapper(RuntimeGuardMapper.class)
                        .selectRevision(RuntimeMutationGuard.AUTH_ACCOUNTS));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    guard.lock(RuntimeMutationGuard.AUTH_ACCOUNTS);
                                    throw new IllegalStateException("Rollback guarded transaction");
                                }));
        assertEquals(
                before + 8,
                sessions.getMapper(RuntimeGuardMapper.class)
                        .selectRevision(RuntimeMutationGuard.AUTH_ACCOUNTS));
    }

    @Test
    void invalidRoleAndNegativeVersionsAreRejected() {
        AuthUser user = user("constraints", AccountRole.VIEWER);
        insert(user);
        assertThrows(
                RuntimeException.class,
                () ->
                        jdbc.update(
                                "UPDATE ifj_auth_user SET role_code='UNKNOWN' WHERE id=?",
                                user.getId()));
        assertThrows(
                RuntimeException.class,
                () ->
                        jdbc.update(
                                "UPDATE ifj_auth_user SET security_version=-1 WHERE id=?",
                                user.getId()));
        assertEquals(AccountRole.VIEWER, users().selectById(user.getId()).getRoleCode());
    }

    private AuthUserMapper users() {
        return sessions.getMapper(AuthUserMapper.class);
    }

    private OperationAuditMapper audits() {
        return sessions.getMapper(OperationAuditMapper.class);
    }

    private void insert(AuthUser user) {
        transaction.executeWithoutResult(status -> assertEquals(1, users().insert(user)));
    }

    private AuthUser user(String prefix, AccountRole role) {
        AuthUser user = new AuthUser();
        long id = ids.incrementAndGet();
        user.setId(id);
        user.setLoginId("user:" + id);
        user.setUsername(prefix + "-" + id);
        user.setDisplayName("显示名-✓");
        user.setPasswordHash("synthetic-test-hash-" + id);
        user.setRoleCode(role);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        user.setSecurityVersion(0L);
        user.setRevision(0L);
        user.setCreatedBy("SYSTEM");
        user.setCreateTime(now());
        user.setUpdateTime(now());
        return user;
    }

    private OperationAudit audit(long target) {
        OperationAudit audit = new OperationAudit();
        audit.setId(ids.incrementAndGet());
        audit.setOccurredAt(now());
        audit.setActorLoginId("legacy-admin");
        audit.setActorName("管理员");
        audit.setActionCode("ACCOUNT_CREATED");
        audit.setTargetType("ACCOUNT");
        audit.setTargetId(Long.toString(target));
        audit.setResultCode("SUCCEEDED");
        audit.setTraceId("0123456789abcdef");
        audit.setRequestId("request-" + target);
        audit.setChangedFields("roleCode,enabled");
        return audit;
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 9, 20, 0, 0, 0, 123000000);
    }

    private Object time(LocalDateTime value) {
        return vendor.equals("sqlite")
                ? value.format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                : Timestamp.valueOf(value);
    }
}
