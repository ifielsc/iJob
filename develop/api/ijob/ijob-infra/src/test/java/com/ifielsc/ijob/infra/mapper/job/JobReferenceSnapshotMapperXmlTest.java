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

class JobReferenceSnapshotMapperXmlTest {
    private static final String MAPPER_RESOURCE = "mapper/job/JobReferenceSnapshotMapper.xml";

    @Test
    void mapperXmlShouldParseAndExposeSnapshotStatements() throws IOException {
        Configuration configuration = parseMapper();

        assertTrue(configuration.hasStatement(namespace("selectAllJobIds"), false));
        assertTrue(configuration.hasStatement(namespace("selectJobReferences"), false));
    }

    @Test
    void jobIdQueryShouldUseOnlyFixedIdProjectionAndOrdering() throws IOException {
        String sql = boundSql(parseMapper(), "selectAllJobIds");

        assertEquals("SELECT id FROM ifj_job ORDER BY id ASC", sql);
    }

    @Test
    void referenceQueryShouldUseFixedJobTypeAndProjection() throws IOException {
        String sql = boundSql(parseMapper(), "selectJobReferences");

        assertTrue(sql.startsWith("SELECT job.id AS jobId, node.ref_id AS referencedJobId"));
        assertTrue(sql.contains("node.definition_id = job.current_definition_id"));
        assertFalse(sql.contains("FROM ifj_job_node"));
        assertTrue(sql.contains("WHERE node.node_type = 'JOB'"));
        assertTrue(sql.endsWith("ORDER BY job.id ASC, node.ref_id ASC, node.id ASC"));
        assertFalse(sql.contains("?"));
    }

    @Test
    void mapperShouldBeReadOnlyAndAvoidTextSubstitution() throws IOException {
        String xml = readMapper();

        assertEquals(2, count(xml, "<select "));
        assertFalse(xml.contains("<insert "));
        assertFalse(xml.contains("<update "));
        assertFalse(xml.contains("<delete "));
        assertFalse(xml.contains("${"));
    }

    private Configuration parseMapper() throws IOException {
        Configuration configuration = new Configuration();
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            XMLMapperBuilder builder =
                    new XMLMapperBuilder(
                            input, configuration, MAPPER_RESOURCE, configuration.getSqlFragments());
            builder.parse();
        }
        return configuration;
    }

    private String boundSql(Configuration configuration, String statementId) {
        return configuration
                .getMappedStatement(namespace(statementId))
                .getBoundSql(null)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String readMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private String namespace(String id) {
        return "com.ifielsc.ijob.infra.mapper.job.JobReferenceSnapshotMapper." + id;
    }
}
