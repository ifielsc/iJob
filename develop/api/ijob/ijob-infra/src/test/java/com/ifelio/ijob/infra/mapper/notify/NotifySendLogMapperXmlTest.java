package com.ifelio.ijob.infra.mapper.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.NotifySendResult;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.infra.entity.notify.NotifySendLog;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

class NotifySendLogMapperXmlTest {
    private static final String MAPPER_RESOURCE = "mapper/notify/NotifySendLogMapper.xml";

    @Test
    void mapperXmlShouldExposeThreeStatements() throws IOException {
        Configuration configuration = parseMapper();

        List.of("insertStarted", "completeIfStarted", "selectDtoList")
                .forEach(
                        id ->
                                assertTrue(
                                        configuration.hasStatement(namespace(id), false),
                                        () -> "Missing mapped statement: " + id));
    }

    @Test
    void queryShouldUseBoundFiltersAndFixedTimeWindow() throws IOException {
        NotifySendLogPageQuery query = new NotifySendLogPageQuery();
        query.setSourceType(NotifySendSource.INSTANCE);
        query.setResult(NotifySendResult.DELIVERY_FAILED);
        query.setNotifyConfigId(7L);
        query.setTargetType(TargetType.TASK);
        query.setTargetId(8L);
        query.setInstanceId(9L);
        query.setFromTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setToTime(LocalDateTime.of(2026, 8, 2, 0, 0));

        String sql =
                normalizedSql(
                        parseMapper()
                                .getMappedStatement(namespace("selectDtoList"))
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_notify_send_log"));
        assertTrue(sql.contains("create_time >= ?"));
        assertTrue(sql.contains("create_time <= ?"));
        assertTrue(sql.contains("source_type = ?"));
        assertTrue(sql.contains("result = ?"));
        assertTrue(sql.contains("notify_config_id = ?"));
        assertTrue(sql.contains("target_type = ?"));
        assertTrue(sql.contains("target_id = ?"));
        assertTrue(sql.contains("instance_id = ?"));
        assertTrue(sql.endsWith("ORDER BY create_time DESC, id DESC"));
    }

    @Test
    void writeStatementsShouldEnforceStartedTransitionWithoutTextSubstitution() throws IOException {
        Configuration configuration = parseMapper();
        NotifySendLog log = new NotifySendLog();
        log.setId(11L);
        log.setSourceType(NotifySendSource.TEST_SEND);
        log.setNotifyConfigId(12L);
        log.setResult(NotifySendResult.DELIVERED);
        log.setCreateTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        log.setUpdateTime(LocalDateTime.of(2026, 8, 1, 0, 1));

        BoundSql insert =
                configuration.getMappedStatement(namespace("insertStarted")).getBoundSql(log);
        BoundSql complete =
                configuration.getMappedStatement(namespace("completeIfStarted")).getBoundSql(log);
        String insertSql = normalizedSql(insert.getSql());
        String completeSql = normalizedSql(complete.getSql());
        String resource = readResource();

        assertTrue(insertSql.contains("INSERT INTO ifj_notify_send_log"));
        assertTrue(insertSql.contains("'STARTED'"));
        assertTrue(completeSql.contains("UPDATE ifj_notify_send_log"));
        assertTrue(completeSql.contains("WHERE id = ?"));
        assertTrue(completeSql.endsWith("AND result = 'STARTED'"));
        assertTrue(insert.getParameterMappings().size() >= 10);
        assertTrue(complete.getParameterMappings().size() >= 7);
        assertFalse(resource.contains("${"));
        assertFalse(resource.contains(" JOIN "));
        assertFalse(resource.contains("DELETE FROM"));
        assertFalse(resource.contains("subject"));
        assertFalse(resource.contains("content"));
        assertFalse(resource.contains("webhook"));
        assertFalse(resource.contains("token"));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(
                            input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }

    private String readResource() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String namespace(String id) {
        return "com.ifelio.ijob.infra.mapper.notify.NotifySendLogMapper." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
