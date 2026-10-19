// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.core.notify.NotifyRateLimitService;
import com.ifielsc.ijob.framework.database.typehandler.SqliteLocalDateTimeTypeHandler;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.mapper.coordination.NotificationOutboxMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotificationDeliveryMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyRateLimitMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

class NotificationChannelsSqliteTest {
    @TempDir Path directory;

    @Test
    void migrationPreservesOldPayloadsAndFencesQuotaDeferral() throws Exception {
        var source = source("migration");
        var flyway =
                Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration/sqlite")
                        .table("ifj_schema_history");
        flyway.target("37").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO ifj_notification_outbox"
                    + " (id,deduplication_key,notify_config_id,notify_type,config_snapshot,config_enabled,subject,content,source_type,status,available_at,max_attempts)"
                    + " VALUES (1,'test',1,'DINGTALK','protected',1,'test','old"
                    + " markdown','SYSTEM','READY',strftime('%Y-%m-%d %H:%M:%f','now'),5)");
        assertEquals(1, flyway.target("38").load().migrate().migrationsExecuted);
        assertEquals(0, flyway.target("38").load().migrate().migrationsExecuted);
        var factory = factory(source);
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(NotificationOutboxMapper.class);
            assertEquals(1, mapper.claim(1L, "owner", "claim", 30_000_000));
            var claimed = mapper.selectByClaimToken("claim");
            assertEquals("old markdown", claimed.getContent());
            assertEquals(0, claimed.getContentVersion());
            assertEquals(0, mapper.defer(1L, "other", "claim", 5_000_000));
            assertEquals(1, mapper.defer(1L, "owner", "claim", 5_000_000));
            assertEquals(0, mapper.defer(1L, "owner", "claim", 5_000_000));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT attempt_count FROM ifj_notification_outbox WHERE id=1",
                            Integer.class));
            assertEquals(
                    "RETRY_WAIT",
                    jdbc.queryForObject(
                            "SELECT status FROM ifj_notification_outbox WHERE id=1", String.class));
            assertTrue(mapper.selectDispatchableIds(10).isEmpty());
            var query = new NotifySendLogPageQuery();
            query.setFromTime(LocalDateTime.now().minusDays(2));
            query.setToTime(LocalDateTime.now().plusDays(2));
            var deliveries =
                    session.getMapper(NotificationDeliveryMapper.class).selectDtoList(query);
            assertEquals(1, deliveries.size());
            assertEquals("RATE_WAIT", deliveries.getFirst().getFailureCode());
            assertFalse(
                    new ObjectMapper()
                            .findAndRegisterModules()
                            .writeValueAsString(deliveries)
                            .contains("protected"));
            assertNotNull(session.getMapper(NotificationDeliveryMapper.class).selectDtoById(1L));
            jdbc.update(
                    "UPDATE ifj_notification_outbox SET "
                            + "available_at=strftime('%Y-%m-%d %H:%M:%f','now'), "
                            + "content_version=1, content=? WHERE id=1",
                    "{\"frozen\":true}");
            assertEquals(1, mapper.claim(1L, "next-owner", "next-claim", 30_000_000));
            var retry = mapper.selectByClaimToken("next-claim");
            assertEquals(1, retry.getContentVersion());
            assertEquals("{\"frozen\":true}", retry.getContent());
            assertEquals(1, mapper.recordResponse(1L, "next-owner", "next-claim", 429));
            assertEquals(1, mapper.complete(1L, "next-owner", "next-claim"));
            assertEquals(
                    null,
                    session.getMapper(NotificationDeliveryMapper.class)
                            .selectDtoById(1L)
                            .getHttpStatus());
        }
    }

    @Test
    void independentConnectionsAllowOnlyOneWinnerAndCooldownCannotShorten() throws Exception {
        var source = source("concurrency");
        new JdbcTemplate(source)
                .execute(
                        "CREATE TABLE ifj_notify_rate_limit (target_key VARCHAR(64) PRIMARY KEY,"
                                + " next_allowed_at BIGINT NOT NULL)");
        var factory = factory(source);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try (var session = factory.openSession(true)) {
                                        var mapper = session.getMapper(NotifyRateLimitMapper.class);
                                        mapper.insertIfAbsent("same-target");
                                        return mapper.acquire("same-target", 1000, 4100);
                                    }
                                }));
            start.countDown();
            int wins = 0;
            for (var future : futures) wins += future.get();
            assertEquals(1, wins);
        }
        try (var session = factory.openSession(true)) {
            var mapper = session.getMapper(NotifyRateLimitMapper.class);
            assertEquals(1, mapper.cooldown("same-target", 9000));
            assertEquals(0, mapper.cooldown("same-target", 5000));
            assertEquals(9000, mapper.selectDeadline("same-target"));
        }
    }

    @Test
    void sameBotAcrossConfigsSharesQuotaWhileEmailNeverTouchesState() throws Exception {
        var source = source("service");
        new JdbcTemplate(source)
                .execute(
                        "CREATE TABLE ifj_notify_rate_limit (target_key VARCHAR(64) PRIMARY KEY,"
                                + " next_allowed_at BIGINT NOT NULL)");
        var factory = factory(source);
        var now = LocalDateTime.parse("2026-10-17T00:00:00");
        try (var first = factory.openSession(true);
                var second = factory.openSession(true)) {
            var a =
                    new NotifyRateLimitService(
                            first.getMapper(NotifyRateLimitMapper.class),
                            () -> now,
                            new ObjectMapper());
            var b =
                    new NotifyRateLimitService(
                            second.getMapper(NotifyRateLimitMapper.class),
                            () -> now,
                            new ObjectMapper());
            var config = new NotifyConfig();
            config.setId(1L);
            config.setType(NotifyType.DINGTALK);
            config.setConfig(
                    "{\"webhook\":\"https://oapi.dingtalk.com/robot/send?access_token=secretCanary\"}");
            assertEquals(0, a.acquire(config));
            config.setId(99L);
            assertEquals(3100, b.acquire(config));
            b.cooldown(config, 60000, false);
            assertEquals(60000, a.acquire(config));
            config.setType(NotifyType.EMAIL);
            config.setConfig("not even JSON");
            assertEquals(0, b.acquire(config));
            b.cooldown(config, 50000, false);
            var keys =
                    new JdbcTemplate(source)
                            .queryForList(
                                    "SELECT target_key FROM ifj_notify_rate_limit", String.class);
            assertEquals(1, keys.size());
            assertFalse(keys.getFirst().contains("secretCanary"));
            assertEquals(64, keys.getFirst().length());
        }
    }

    private DriverManagerDataSource source(String name) {
        return new DriverManagerDataSource(
                "jdbc:sqlite:" + directory.resolve(name + ".db") + "?busy_timeout=10000");
    }

    private SqlSessionFactory factory(DriverManagerDataSource source) throws Exception {
        var c = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        c.setDatabaseId("sqlite");
        c.setMapUnderscoreToCamelCase(true);
        c.getTypeHandlerRegistry()
                .register(LocalDateTime.class, new SqliteLocalDateTimeTypeHandler());
        for (String path :
                List.of(
                        "mapper/notify/NotifyRateLimitMapper.xml",
                        "mapper/notify/NotificationDeliveryMapper.xml",
                        "mapper/coordination/NotificationOutboxMapper.xml"))
            try (var input = new ClassPathResource(path).getInputStream()) {
                new XMLMapperBuilder(input, c, path, c.getSqlFragments()).parse();
            }
        return new SqlSessionFactoryBuilder().build(c);
    }
}
