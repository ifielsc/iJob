package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.contract.enums.InstanceStatus;
import com.ifelio.ijob.infra.coordination.work.ExecutionFencePermit;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.mapper.runtime.InstanceMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;

class PausedCompletionTest {
    @TempDir Path directory;

    @Test
    void completedStatementCanPublishWhilePausedAndCompletionCanRaceResume() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("pause.db"));
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE ifj_instance (id INTEGER PRIMARY KEY, status TEXT, output_variables"
                        + " TEXT, update_time TEXT, progress INTEGER, end_time TEXT, message TEXT,"
                        + " error_code TEXT, message_key TEXT, message_args TEXT, execution_fence"
                        + " INTEGER, trigger_id INTEGER)");
        jdbc.execute(
                "CREATE TABLE ifj_trigger_work_item (trigger_id INTEGER, status TEXT, claim_token"
                        + " TEXT, fence_token INTEGER, lease_deadline TEXT)");
        jdbc.update(
                "INSERT INTO ifj_instance(id,status,execution_fence,trigger_id)"
                        + " VALUES(1,'PAUSED',7,2)");
        jdbc.update("INSERT INTO ifj_trigger_work_item VALUES(2,'CLAIMED','owner',7,'9999-01-01')");
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setDatabaseId("sqlite");
        String resource = "mapper/runtime/InstanceMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var mapper = session.getMapper(InstanceMapper.class);
            var permit = new ExecutionFencePermit(ExecutionFencePermit.Mode.CLAIMED, "owner", 7L);
            var stale = new ExecutionFencePermit(ExecutionFencePermit.Mode.CLAIMED, "owner", 6L);
            assertEquals(0, mapper.updateOutputVariablesIfRunning(1L, "{}", stale));
            assertEquals(1, mapper.updateOutputVariablesIfRunning(1L, "{\"count\":1}", permit));
            var completion = new Instance();
            completion.setId(1L);
            completion.setStatus(InstanceStatus.SUCCESS);
            completion.setEndTime(LocalDateTime.now());
            assertEquals(0, mapper.completeIfCurrent(completion, InstanceStatus.RUNNING, stale));
            assertEquals(1, mapper.completeIfCurrent(completion, InstanceStatus.RUNNING, permit));
            for (String terminal : new String[] {"STOPPED", "TIMED_OUT", "FAILED", "SUCCESS"}) {
                jdbc.update("UPDATE ifj_instance SET status=?", terminal);
                assertEquals(0, mapper.updateOutputVariablesIfRunning(1L, "{}", permit));
                assertEquals(
                        0, mapper.completeIfCurrent(completion, InstanceStatus.PAUSED, permit));
            }
        }
    }
}
