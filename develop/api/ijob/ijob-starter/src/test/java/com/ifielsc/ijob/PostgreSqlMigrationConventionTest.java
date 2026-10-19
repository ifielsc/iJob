// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PostgreSqlMigrationConventionTest {
    private static final String BASELINE = "db/migration/postgresql/V19__platform_baseline.sql";

    @Test
    void baselineShouldDescribeTheCompletePostgresqlV19Schema() throws Exception {
        String sql = read(BASELINE);
        String normalized = sql.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

        assertEquals(35, count(sql, "(?im)^CREATE TABLE ifj_"));
        assertEquals(40, count(sql, "(?im)^ALTER TABLE .* FOREIGN KEY"));
        assertTrue(normalized.contains("create table ifj_schema_capability"));
        assertTrue(normalized.contains("'platform-schema', 19, true"));
        assertTrue(normalized.contains("on delete restrict"));
        assertFalse(
                Pattern.compile(
                                "(?i)auto_increment|engine\\s*=|charset|tinyint|longtext|"
                                        + "datetime|on duplicate|insert ignore|timestampadd|"
                                        + "timestampdiff|find_in_set")
                        .matcher(sql)
                        .find());
    }

    @Test
    void postgresqlMapperOverridesShouldCoverEveryMysqlSpecificStatement() throws Exception {
        var resolver =
                new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath*:mapper/**/*.xml");
        int postgresqlStatements = 0;
        int genericStatements = 0;
        int mapperFiles = 0;
        Set<String> genericIds = new HashSet<>();
        Set<String> postgresqlIds = new HashSet<>();
        Pattern namespacePattern = Pattern.compile("<mapper\\s+namespace=\\\"([^\\\"]+)\\\"");
        Pattern statementStart =
                Pattern.compile(
                        "<(select|insert|update|delete)\\b([^>]*)\\bid=\\\"([^\\\"]+)\\\"([^>]*)>");
        Pattern postgresqlStatement =
                Pattern.compile(
                        "(?s)<(select|insert|update|delete)[^>]*databaseId=\\\"postgresql\\\"[^>]*>.*?</\\1>");
        for (var resource : resources) {
            String xml =
                    new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher namespaceMatcher = namespacePattern.matcher(xml);
            assertTrue(namespaceMatcher.find(), resource::getDescription);
            String namespace = namespaceMatcher.group(1);
            Matcher startMatcher = statementStart.matcher(xml);
            while (startMatcher.find()) {
                String attributes = startMatcher.group(2) + startMatcher.group(4);
                String qualifiedId = namespace + "." + startMatcher.group(3);
                if (attributes.contains("databaseId=\"postgresql\"")) {
                    postgresqlIds.add(qualifiedId);
                } else if (!attributes.contains("databaseId=")) {
                    genericStatements++;
                    genericIds.add(qualifiedId);
                }
            }
            Matcher matcher = postgresqlStatement.matcher(xml);
            boolean found = false;
            while (matcher.find()) {
                found = true;
                postgresqlStatements++;
                assertFalse(
                        Pattern.compile(
                                        "(?i)on duplicate|insert ignore|timestampadd|timestampdiff|"
                                                + "find_in_set|&lt;=(?:&gt;|>)")
                                .matcher(matcher.group())
                                .find(),
                        resource::getDescription);
            }
            if (found) {
                mapperFiles++;
            }
        }
        assertEquals(60, resources.length);
        assertEquals(356, genericStatements);
        assertFalse(
                genericIds.contains(
                        "com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyResourceMapper.selectAll"));
        assertTrue(
                genericIds.contains(
                        "com.ifielsc.ijob.infra.mapper.concurrency.ConcurrencyResourceMapper.selectPage"));
        assertEquals(23, mapperFiles);
        assertEquals(54, postgresqlStatements);
        assertEquals(genericStatements, genericIds.size());
        assertEquals(postgresqlStatements, postgresqlIds.size());
        assertTrue(genericIds.containsAll(postgresqlIds));
    }

    private String read(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private int count(String value, String regex) {
        int result = 0;
        Matcher matcher = Pattern.compile(regex).matcher(value);
        while (matcher.find()) {
            result++;
        }
        return result;
    }
}
