package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ifelio.ijob.contract.enums.RuntimeCommandStatus;
import com.ifelio.ijob.infra.mapper.coordination.RuntimeCommandMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

class RuntimeCommandStopCompletionTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"STOPPED", "SUCCESS", "FAILED", "TIMED_OUT", "SKIPPED"})
    void completedTerminalRootCanAcknowledgeOnlyItsOriginalCommandFence(String terminalStatus)
            throws Exception {
        DriverManagerDataSource source =
                new DriverManagerDataSource(
                        "jdbc:sqlite:" + directory.resolve("command.db").toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE ifj_runtime_command (request_id TEXT PRIMARY KEY, trigger_id INTEGER,"
                    + " instance_id INTEGER, root_instance_id INTEGER, command_type TEXT, status"
                    + " TEXT, processing_owner TEXT, processing_token TEXT, processing_fence"
                    + " INTEGER, result_message TEXT, processed_at TEXT, update_time TEXT,"
                    + " expires_at TEXT)");
        jdbc.execute(
                "CREATE TABLE ifj_trigger_work_item (trigger_id INTEGER PRIMARY KEY,"
                        + " root_instance_id INTEGER, status TEXT, lease_owner TEXT, fence_token"
                        + " INTEGER, lease_deadline TEXT)");
        jdbc.execute(
                "CREATE TABLE ifj_instance (id INTEGER PRIMARY KEY, status TEXT, execution_fence"
                        + " INTEGER)");
        jdbc.update(
                "INSERT INTO ifj_runtime_command VALUES"
                    + " ('request',1,2,2,'STOP','PROCESSING','node','claim',7,NULL,NULL,NULL,'9999-01-01')");
        jdbc.update("INSERT INTO ifj_trigger_work_item VALUES (1,2,'COMPLETED',NULL,7,NULL)");
        jdbc.update("INSERT INTO ifj_instance VALUES (2,?,7)", terminalStatus);
        Configuration configuration =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setDatabaseId("sqlite");
        String resource = "mapper/coordination/RuntimeCommandMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments())
                    .parse();
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            RuntimeCommandMapper mapper = session.getMapper(RuntimeCommandMapper.class);
            assertEquals(
                    0,
                    mapper.finish(
                            "request", "other", 7, RuntimeCommandStatus.SUCCEEDED, "ignored"));
            assertEquals(
                    0,
                    mapper.finish(
                            "request", "claim", 6, RuntimeCommandStatus.SUCCEEDED, "ignored"));
            jdbc.update("UPDATE ifj_trigger_work_item SET fence_token=8");
            assertEquals(
                    0,
                    mapper.finish(
                            "request", "claim", 7, RuntimeCommandStatus.SUCCEEDED, "ignored"));
            jdbc.update("UPDATE ifj_trigger_work_item SET fence_token=7");
            jdbc.update("UPDATE ifj_instance SET status='RUNNING'");
            assertEquals(
                    0,
                    mapper.finish(
                            "request", "claim", 7, RuntimeCommandStatus.SUCCEEDED, "ignored"));
            jdbc.update("UPDATE ifj_instance SET status=?", terminalStatus);
            jdbc.update("UPDATE ifj_runtime_command SET command_type='PAUSE'");
            assertEquals(
                    0,
                    mapper.finish(
                            "request", "claim", 7, RuntimeCommandStatus.SUCCEEDED, "ignored"));
            jdbc.update("UPDATE ifj_runtime_command SET command_type='STOP'");
            assertEquals(
                    0,
                    mapper.finish("request", "claim", 7, RuntimeCommandStatus.FAILED, "ignored"));
            assertEquals(
                    1,
                    mapper.finish(
                            "request", "claim", 7, RuntimeCommandStatus.SUCCEEDED, "stopped"));
            assertEquals(
                    "SUCCEEDED",
                    jdbc.queryForObject("SELECT status FROM ifj_runtime_command", String.class));
            assertEquals(
                    0,
                    mapper.finish("request", "claim", 7, RuntimeCommandStatus.SUCCEEDED, "replay"));
            jdbc.update(
                    "UPDATE ifj_runtime_command SET status='PENDING', processing_owner=NULL,"
                            + " processing_token=NULL, processing_fence=NULL");
            jdbc.update("UPDATE ifj_trigger_work_item SET status='CLAIMED'");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_trigger_work_item SET status='COMPLETED', fence_token=8");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_trigger_work_item SET fence_token=7");
            jdbc.update("UPDATE ifj_instance SET status='RUNNING'");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_instance SET status=?", terminalStatus);
            jdbc.update("UPDATE ifj_runtime_command SET command_type='PAUSE'");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_runtime_command SET command_type='STOP', status='PROCESSING'");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_runtime_command SET status='PENDING', expires_at='2000-01-01'");
            assertEquals(0, mapper.succeedCompletedStops(1));
            jdbc.update("UPDATE ifj_runtime_command SET expires_at='9999-01-01'");
            jdbc.update(
                    "INSERT INTO ifj_runtime_command SELECT 'second', trigger_id, instance_id,"
                            + " root_instance_id, command_type, status, processing_owner,"
                            + " processing_token, processing_fence, result_message, processed_at,"
                            + " update_time, expires_at FROM ifj_runtime_command");
            assertEquals(1, mapper.succeedCompletedStops(1));
            assertEquals(1, mapper.succeedCompletedStops(1));
            assertEquals(0, mapper.succeedCompletedStops(1));
        }
    }
}
