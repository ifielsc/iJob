package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.core.trigger.TriggerCommand;
import com.ifelio.ijob.core.trigger.TriggerCommandDispatcher;
import com.ifelio.ijob.core.trigger.TriggerCommandHandler;
import com.ifelio.ijob.infra.coordination.work.DatabaseTriggerWorkQueue;
import com.ifelio.ijob.infra.coordination.work.TriggerWorkItem;
import com.ifelio.ijob.infra.mapper.coordination.TriggerWorkItemMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class WorkClaimAfterCommitTest {
    @TempDir Path directory;

    @Test
    void claimAndUnstartedDeferralMustCommitBeforeReturningFromAfterCommit() throws Exception {
        DriverManagerDataSource source =
                new DriverManagerDataSource(
                        "jdbc:sqlite:" + directory.resolve("claim.db").toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE claim_probe (id INTEGER PRIMARY KEY, status TEXT, token TEXT)");
        jdbc.update("INSERT INTO claim_probe VALUES (1, 'READY', NULL)");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        TriggerWorkItemMapper mapper =
                (TriggerWorkItemMapper)
                        Proxy.newProxyInstance(
                                TriggerWorkItemMapper.class.getClassLoader(),
                                new Class<?>[] {TriggerWorkItemMapper.class},
                                (proxy, method, arguments) ->
                                        switch (method.getName()) {
                                            case "claimByTriggerId" ->
                                                    jdbc.update(
                                                            "UPDATE claim_probe SET"
                                                                + " status='CLAIMED', token=? WHERE"
                                                                + " id=1 AND status='READY'",
                                                            arguments[2]);
                                            case "selectByClaimToken" -> {
                                                TriggerWorkItem item = new TriggerWorkItem();
                                                item.setTriggerId(1L);
                                                item.setLeaseOwner("node-a");
                                                item.setClaimToken((String) arguments[0]);
                                                item.setFenceToken(1L);
                                                item.setAttemptCount(1);
                                                item.setMaxAttempts(5);
                                                item.setLeaseDeadline(
                                                        LocalDateTime.now().plusSeconds(30));
                                                yield item;
                                            }
                                            case "deferUnstarted" ->
                                                    jdbc.update(
                                                            "UPDATE claim_probe SET"
                                                                + " status='RETRY_WAIT' WHERE id=1"
                                                                + " AND token=?",
                                                            arguments[2]);
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        ProxyFactory factory =
                new ProxyFactory(new DatabaseTriggerWorkQueue(mapper, new AppProperties()));
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        DatabaseTriggerWorkQueue queue = (DatabaseTriggerWorkQueue) factory.getProxy();
        List<Runnable> scheduled = new ArrayList<>();
        TriggerCommandHandler handler =
                new TriggerCommandHandler(null, null, null) {
                    @Override
                    public void handle(TriggerCommand command) {
                        var claim = queue.claim(command.getTriggerId(), "node-a");
                        assertNotNull(claim);
                        assertEquals("CLAIMED", committedStatus(source));
                        assertTrue(queue.deferUnstarted(claim, "executor saturated"));
                        assertEquals("RETRY_WAIT", committedStatus(source));
                    }
                };
        TriggerCommandDispatcher dispatcher = new TriggerCommandDispatcher(handler, scheduled::add);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            jdbc.update("UPDATE claim_probe SET status='READY' WHERE id=1");
                            dispatcher.dispatchAfterCommit(1L);
                            assertTrue(scheduled.isEmpty());
                        });
        assertEquals(1, scheduled.size());
        assertEquals("READY", committedStatus(source));
        scheduled.getFirst().run();
        assertEquals("RETRY_WAIT", committedStatus(source));
    }

    private static String committedStatus(DriverManagerDataSource source) {
        try (var connection = source.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT status FROM claim_probe WHERE id=1")) {
            assertTrue(rows.next());
            return rows.getString(1);
        } catch (Exception failure) {
            throw new AssertionError("Independent connection cannot read committed claim", failure);
        }
    }
}
