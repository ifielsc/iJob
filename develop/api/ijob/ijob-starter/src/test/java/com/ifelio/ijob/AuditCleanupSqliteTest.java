package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.core.auth.AuditService;
import com.ifelio.ijob.infra.mapper.auth.OperationAuditMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;

class AuditCleanupSqliteTest {
    @TempDir Path directory;

    @Test
    void cleanupMustShareLeaseWriteLockAndRollback() {
        var fixture = fixture("rollback");
        fixture.transaction.executeWithoutResult(
                status -> {
                    fixture.jdbc.update("UPDATE lease_guard SET fence = fence + 1");
                    assertEquals(1, fixture.audit.cleanup(LocalDateTime.now(), 100, false));
                    assertEquals(
                            0,
                            fixture.jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM audit_sample", Integer.class));
                    status.setRollbackOnly();
                });
        assertEquals(
                1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM audit_sample", Integer.class));
        assertEquals(
                0, fixture.jdbc.queryForObject("SELECT fence FROM lease_guard", Integer.class));
    }

    @Test
    void dryRunMustReadUnderLeaseWithoutDeleting() {
        var fixture = fixture("preview");
        fixture.transaction.executeWithoutResult(
                status -> {
                    fixture.jdbc.update("UPDATE lease_guard SET fence = fence + 1");
                    assertEquals(1, fixture.audit.cleanup(LocalDateTime.now(), 100, true));
                });
        assertEquals(
                1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM audit_sample", Integer.class));
        assertEquals(
                1, fixture.jdbc.queryForObject("SELECT fence FROM lease_guard", Integer.class));
    }

    private Fixture fixture(String name) {
        var source =
                new DriverManagerDataSource(
                        "jdbc:sqlite:"
                                + directory.resolve(name + ".db")
                                + "?transaction_mode=IMMEDIATE&busy_timeout=100");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE audit_sample (id INTEGER PRIMARY KEY)");
        jdbc.execute("INSERT INTO audit_sample VALUES (1)");
        jdbc.execute("CREATE TABLE lease_guard (fence INTEGER)");
        jdbc.execute("INSERT INTO lease_guard VALUES (0)");
        OperationAuditMapper mapper =
                (OperationAuditMapper)
                        Proxy.newProxyInstance(
                                OperationAuditMapper.class.getClassLoader(),
                                new Class<?>[] {OperationAuditMapper.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "selectExpired" ->
                                                    jdbc.queryForList(
                                                            "SELECT id FROM audit_sample",
                                                            Long.class);
                                            case "deleteExpired" ->
                                                    jdbc.update(
                                                            "DELETE FROM audit_sample WHERE id=1");
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        var manager = new DataSourceTransactionManager(source);
        return new Fixture(
                jdbc,
                new TransactionTemplate(manager),
                new AuditService(mapper, namespace -> 1L, manager));
    }

    private record Fixture(
            JdbcTemplate jdbc, TransactionTemplate transaction, AuditService audit) {}
}
