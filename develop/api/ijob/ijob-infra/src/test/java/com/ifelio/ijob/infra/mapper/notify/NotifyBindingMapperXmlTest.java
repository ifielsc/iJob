package com.ifelio.ijob.infra.mapper.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.TargetType;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class NotifyBindingMapperXmlTest {
    private static final String BINDING_MAPPER = "mapper/notify/NotifyBindingMapper.xml";
    private static final String CONFIG_MAPPER = "mapper/notify/NotifyConfigMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeBindingStatements() throws IOException {
        Configuration configuration = parseMappers();
        List<String> statements =
                List.of(
                        "selectDtoListByTarget",
                        "selectDtoById",
                        "selectById",
                        "selectByTargetAndConfig",
                        "selectListByTargetId",
                        "insert",
                        "updatePolicyById",
                        "deleteById",
                        "selectIdsForUpdate",
                        "deleteByIds",
                        "countByNotifyConfigIds",
                        "deleteByTargetId",
                        "deleteByTargetIds");

        statements.forEach(
                id ->
                        assertTrue(
                                configuration.hasStatement(namespace(id), false),
                                () -> "Missing mapped statement: " + id));
        assertTrue(
                configuration.hasStatement(
                        "com.ifelio.ijob.infra.mapper.notify." + "NotifyConfigMapper.countById",
                        false));
    }

    @Test
    void dtoListShouldJoinOnlySafeConfigMetadata() throws IOException {
        Configuration configuration = parseMappers();
        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("selectDtoListByTarget"))
                                .getBoundSql(Map.of("targetType", TargetType.TASK, "targetId", 7L))
                                .getSql());

        assertTrue(sql.contains("FROM ifj_notify_binding nb"));
        assertTrue(sql.contains("JOIN ifj_notify_config nc ON nc.id = nb.notify_config_id"));
        assertTrue(sql.contains("nb.target_type = ?"));
        assertTrue(sql.contains("nb.target_id = ?"));
        assertTrue(sql.endsWith("ORDER BY nb.id ASC"));
        assertFalse(sql.matches("(?s).*\\bnc\\.config\\b.*"));
        assertFalse(readResource(BINDING_MAPPER).contains("${"));
    }

    @Test
    void batchTargetDeleteShouldBindEveryId() throws IOException {
        Configuration configuration = parseMappers();
        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("deleteByTargetIds"))
                                .getBoundSql(
                                        Map.of(
                                                "targetType",
                                                TargetType.JOB,
                                                "targetIds",
                                                List.of(3L, 4L)))
                                .getSql());

        assertTrue(sql.startsWith("DELETE FROM ifj_notify_binding"));
        assertTrue(sql.contains("target_type = ?"));
        assertTrue(sql.contains("target_id IN ( ? , ? )"));
        assertFalse(readResource(BINDING_MAPPER).contains("${"));
    }

    @Test
    void managementBatchDeleteAndReferenceCountShouldBeEmptySafe() throws IOException {
        Configuration configuration = parseMappers();
        String lock =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("selectIdsForUpdate"))
                                .getBoundSql(Map.of("ids", List.of(2L, 1L)))
                                .getSql());
        String count =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("countByNotifyConfigIds"))
                                .getBoundSql(Map.of("notifyConfigIds", List.of(5L, 6L)))
                                .getSql());
        String emptyDelete =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("deleteByIds"))
                                .getBoundSql(Map.of("ids", List.of()))
                                .getSql());

        assertTrue(lock.endsWith("ORDER BY id FOR UPDATE"));
        assertTrue(count.contains("notify_config_id IN ( ? , ? )"));
        assertTrue(emptyDelete.contains("WHERE 1 = 0"));
    }

    private Configuration parseMappers() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        parse(configuration, BINDING_MAPPER);
        parse(configuration, CONFIG_MAPPER);
        return configuration;
    }

    private void parse(Configuration configuration, String name) throws IOException {
        ClassPathResource resource = new ClassPathResource(name);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, name, configuration.getSqlFragments())
                    .parse();
        }
    }

    private String readResource(String name) throws IOException {
        ClassPathResource resource = new ClassPathResource(name);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String namespace(String id) {
        return "com.ifelio.ijob.infra.mapper.notify.NotifyBindingMapper." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
