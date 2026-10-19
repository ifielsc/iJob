package com.ifielsc.ijob.infra.mapper.task;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.contract.job.JobPageQuery;
import com.ifielsc.ijob.contract.task.TaskPageQuery;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

class UnifiedListMapperXmlTest {
    @Test
    void exactIdentityAndPublishedNodeCountAreBoundInEveryDialect() throws Exception {
        for (String dialect : List.of("mysql", "postgresql", "sqlite", "oracle", "sqlserver")) {
            Configuration c = new Configuration();
            c.setDatabaseId(dialect);
            for (String resource :
                    List.of(
                            "mapper/task/TaskMapper.xml",
                            "mapper/job/JobCurrentDefinitionMapper.xml",
                            "mapper/task/TaskHistoryMapper.xml")) {
                try (var input = new ClassPathResource(resource).getInputStream()) {
                    new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
                }
            }
            var task = new TaskPageQuery();
            task.setId(9007199254740993L);
            task.setName("finance");
            task.setGroup("batch");
            var job = new JobPageQuery();
            job.setId(9007199254740993L);
            job.setName("finance");
            job.setGroup("batch");
            var taskSql =
                    c.getMappedStatement(
                                    "com.ifielsc.ijob.infra.mapper.task.TaskMapper.selectDtoList")
                            .getBoundSql(task);
            var jobSql =
                    c.getMappedStatement(
                                    "com.ifielsc.ijob.infra.mapper.job.JobCurrentDefinitionMapper.selectDtoList")
                            .getBoundSql(job);
            assertTrue(taskSql.getSql().contains("AND id = ?"), dialect);
            String jobSqlText = jobSql.getSql().replace("job_identity", "identity");
            assertTrue(jobSqlText.contains("AND identity.id = ?"), dialect);
            assertTrue(
                    jobSqlText.contains("n.definition_id = identity.current_definition_id"),
                    dialect);
            assertTrue(jobSql.getSql().contains("AS node_count"), dialect);
            assertTrue(
                    taskSql.getParameterMappings().stream()
                            .anyMatch(p -> p.getProperty().equals("id")));
            assertTrue(
                    jobSql.getParameterMappings().stream()
                            .anyMatch(p -> p.getProperty().equals("id")));
            var history =
                    c.getMappedStatement(
                                    "com.ifielsc.ijob.infra.mapper.task.TaskHistoryMapper.selectVersions")
                            .getBoundSql(1L)
                            .getSql();
            assertFalse(history.contains("plugin_config"));
            assertTrue(history.contains("ORDER BY revision DESC, id DESC"));
        }
    }
}
