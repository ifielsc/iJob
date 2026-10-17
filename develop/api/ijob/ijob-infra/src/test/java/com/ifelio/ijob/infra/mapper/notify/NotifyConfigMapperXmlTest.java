package com.ifelio.ijob.infra.mapper.notify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.NotifyType;
import com.ifelio.ijob.contract.notify.NotifyConfigPageQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class NotifyConfigMapperXmlTest {
    private static final String NOTIFY_CONFIG_MAPPER = "mapper/notify/NotifyConfigMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeCrudStatements() throws IOException {
        Configuration configuration = parseMapper();
        List<String> statements =
                List.of(
                        "selectDtoList",
                        "selectById",
                        "insert",
                        "updateById",
                        "deleteById",
                        "selectIdsForUpdate",
                        "deleteByIds");

        statements.forEach(
                id ->
                        assertTrue(
                                configuration.hasStatement(namespace(id), false),
                                () -> "Missing mapped statement: " + id));
    }

    @Test
    void pageSqlShouldOmitConfigAndUseOnlyBoundFilters() throws IOException {
        Configuration configuration = parseMapper();
        NotifyConfigPageQuery query = new NotifyConfigPageQuery();
        query.setName("mail");
        query.setType(NotifyType.EMAIL);
        query.setEnabled(true);

        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("selectDtoList"))
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_notify_config"));
        assertFalse(sql.substring(0, sql.indexOf("FROM")).matches("(?s).*\\bconfig\\b.*"));
        assertTrue(sql.contains("name LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("type = ?"));
        assertTrue(sql.contains("enabled = ?"));
        assertTrue(sql.endsWith("ORDER BY update_time DESC, id DESC"));
        assertFalse(readResource().contains("${"));
    }

    @Test
    void writeSqlShouldBindConfigWithoutTextSubstitution() throws IOException {
        String resource = readResource();

        assertTrue(resource.contains("#{config}"));
        assertTrue(resource.contains("UPDATE ifj_notify_config"));
        assertTrue(resource.contains("DELETE FROM ifj_notify_config"));
        assertFalse(resource.contains("${"));
    }

    @Test
    void batchDeleteShouldLockInOrderAndBeEmptySafe() throws IOException {
        Configuration configuration = parseMapper();
        String lock =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("selectIdsForUpdate"))
                                .getBoundSql(Map.of("ids", List.of(2L, 1L)))
                                .getSql());
        String emptyDelete =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("deleteByIds"))
                                .getBoundSql(Map.of("ids", List.of()))
                                .getSql());

        assertTrue(lock.endsWith("ORDER BY id FOR UPDATE"));
        assertTrue(emptyDelete.contains("WHERE 1 = 0"));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(NOTIFY_CONFIG_MAPPER);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input,
                            configuration,
                            NOTIFY_CONFIG_MAPPER,
                            configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private String readResource() throws IOException {
        ClassPathResource resource = new ClassPathResource(NOTIFY_CONFIG_MAPPER);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String namespace(String id) {
        return "com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
