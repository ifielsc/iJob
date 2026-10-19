// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.infra.mapper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class JobMapperXmlTest {
    private static final String MAPPER = "mapper/job/JobMapper.xml";

    @Test
    void legacyMapperShouldRetainOnlyActiveIdentityLookup() throws IOException {
        Configuration configuration = parseMapper();

        assertEquals(
                List.of("selectById"),
                java.util.Arrays.stream(JobMapper.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName)
                        .toList());
        String sql =
                configuration
                        .getMappedStatement(JobMapper.class.getName() + ".selectById")
                        .getBoundSql(1L)
                        .getSql()
                        .replaceAll("\\s+", " ")
                        .trim();
        assertTrue(sql.contains("FROM ifj_job WHERE id = ?"));
        assertTrue(sql.contains("AND retired = FALSE"));
        assertTrue(sql.contains("AND retired_time IS NULL"));
        assertTrue(sql.contains("current_definition_id IS NOT NULL"));
        String mapper = readMapper();
        assertFalse(mapper.contains("${"));
        assertFalse(mapper.contains("<insert "));
        assertFalse(mapper.contains("<update "));
        assertFalse(mapper.contains("<delete "));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, MAPPER, configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }

    private String readMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
