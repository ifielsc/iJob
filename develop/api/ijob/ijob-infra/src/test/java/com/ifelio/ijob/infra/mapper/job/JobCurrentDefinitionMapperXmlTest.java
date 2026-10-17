package com.ifelio.ijob.infra.mapper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.job.JobPageQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

class JobCurrentDefinitionMapperXmlTest {
    private static final String RESOURCE = "mapper/job/JobCurrentDefinitionMapper.xml";

    @Test
    void listShouldJoinExactCurrentDefinitionAndFilterActiveIdentity() throws IOException {
        JobPageQuery query = new JobPageQuery();
        query.setName("daily");
        query.setEnabled(true);
        query.setGroup("gpu");

        String sql = boundSql("selectDtoList", query);

        assertTrue(sql.contains("FROM ifj_job identity "));
        assertTrue(sql.contains("JOIN ifj_job_definition definition "));
        assertTrue(sql.contains("definition.id = identity.current_definition_id"));
        assertTrue(sql.contains("definition.revision = identity.current_revision"));
        assertTrue(sql.contains("identity.retired = FALSE"));
        assertTrue(sql.contains("identity.retired_time IS NULL"));
        assertTrue(sql.contains("definition.name LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("definition.enabled = ?"));
        assertTrue(sql.contains("FROM ifj_job_definition_group definition_group"));
        assertTrue(sql.contains("definition_group.definition_id = definition.id"));
        assertTrue(sql.contains("definition_group.group_code = ?"));
        assertFalse(sql.contains("ifj_job_node"));
    }

    @Test
    void detailIdentityReadShouldExposePointerAndRetirementState() throws IOException {
        String sql = boundSql("selectById", 8L);

        assertEquals(
                "SELECT id, current_definition_id, current_revision, "
                        + "retired, retired_time, create_time, update_time "
                        + "FROM ifj_job WHERE id = ?",
                sql);
    }

    @Test
    void mapperShouldExposeTwoReadOnlyStatements() throws IOException {
        Configuration configuration = configuration();
        String namespace = JobCurrentDefinitionMapper.class.getName() + ".";

        assertEquals(
                SqlCommandType.SELECT,
                configuration.getMappedStatement(namespace + "selectDtoList").getSqlCommandType());
        assertEquals(
                SqlCommandType.SELECT,
                configuration.getMappedStatement(namespace + "selectById").getSqlCommandType());
    }

    private String boundSql(String id, Object parameter) throws IOException {
        return configuration()
                .getMappedStatement(JobCurrentDefinitionMapper.class.getName() + "." + id)
                .getBoundSql(parameter)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, RESOURCE, configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }
}
