// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.ResourcePageQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class ResourceMapperXmlTest {
    private static final String RESOURCE_MAPPER = "mapper/resource/ResourceMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeCrudStatements() throws IOException {
        Configuration configuration = parseMapper();
        List<String> statements =
                List.of(
                        "selectDtoList",
                        "selectById",
                        "selectByCode",
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
    void pageSqlShouldUseBoundFiltersAndFixedOrdering() throws IOException {
        Configuration configuration = parseMapper();
        ResourcePageQuery query = new ResourcePageQuery();
        query.setCode("db");
        query.setName("demo");
        query.setType(ResourceType.DB);
        query.setEnabled(true);

        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("selectDtoList"))
                                .getBoundSql(query)
                                .getSql());

        assertTrue(sql.contains("FROM ifj_resource"));
        assertTrue(sql.contains("code LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("name LIKE CONCAT('%', ?, '%')"));
        assertTrue(sql.contains("type = ?"));
        assertTrue(sql.contains("enabled = ?"));
        assertTrue(sql.endsWith("ORDER BY update_time DESC, id DESC"));
        assertFalse(readResource().contains("${"));
    }

    @Test
    void batchDeleteShouldBindEveryIdWithoutTextSubstitution() throws IOException {
        Configuration configuration = parseMapper();
        String sql =
                normalizedSql(
                        configuration
                                .getMappedStatement(namespace("deleteByIds"))
                                .getBoundSql(Map.of("ids", List.of(1L, 2L)))
                                .getSql());

        assertTrue(sql.startsWith("DELETE FROM ifj_resource"));
        assertTrue(sql.contains("id IN ( ? , ? )"));
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
        assertFalse(readResource().contains("${"));
    }

    @Test
    void publicationAndDeletionLocksMustKeepTheirVendorSemantics() throws IOException {
        for (String vendor : List.of("mysql", "postgresql", "oracle", "sqlserver", "sqlite")) {
            String sql =
                    normalizedSql(
                            parseMapper(vendor)
                                    .getMappedStatement(namespace("selectIdsForUpdate"))
                                    .getBoundSql(Map.of("ids", List.of(1L, 2L)))
                                    .getSql());
            if (vendor.equals("sqlserver")) {
                assertTrue(sql.contains("WITH (UPDLOCK, HOLDLOCK, ROWLOCK)"), sql);
            } else if (vendor.equals("sqlite")) {
                assertFalse(
                        sql.contains("FOR UPDATE")); // IMMEDIATE transactions serialize writers.
            } else {
                assertTrue(sql.endsWith("ORDER BY id FOR UPDATE"), sql);
            }
        }
    }

    private Configuration parseMapper() throws IOException {
        return parseMapper(null);
    }

    private Configuration parseMapper(String vendor) throws IOException {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(vendor);
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(RESOURCE_MAPPER);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, RESOURCE_MAPPER, configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private String readResource() throws IOException {
        ClassPathResource resource = new ClassPathResource(RESOURCE_MAPPER);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String namespace(String id) {
        return "com.ifielsc.ijob.infra.mapper.resource.ResourceMapper." + id;
    }

    private String normalizedSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
