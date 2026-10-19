package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.infra.coordination.work.ExecutionFencePermit;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

class InstancePermitDialectTest {
    private static final String MAPPER = "mapper/runtime/InstanceMapper.xml";
    private static final String UPDATE =
            "com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper.markPausedIfRunning";

    @ParameterizedTest
    @CsvSource({
        "mysql,CURRENT_TIMESTAMP(3)",
        "postgresql,CURRENT_TIMESTAMP(3)",
        "sqlite,strftime(",
        "oracle,SYS_EXTRACT_UTC",
        "sqlserver,SYSUTCDATETIME()"
    })
    void claimedPermitUsesDatabaseClockWithoutRemovingFences(String dialect, String clock)
            throws Exception {
        String sql =
                configuration(dialect)
                        .getMappedStatement(UPDATE)
                        .getBoundSql(parameters("claim"))
                        .getSql();
        String predicate = sql.substring(sql.indexOf("AND ifj_instance.execution_fence"));
        assertTrue(predicate.contains(clock), predicate);
        assertTrue(predicate.contains("work_item.claim_token = ?"), predicate);
        assertTrue(predicate.contains("work_item.fence_token = ?"), predicate);
    }

    @Test
    void sqliteAllowsCurrentClaimButRejectsExpiredOrWrongClaim() throws Exception {
        Configuration config = configuration("sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE ifj_instance(id INTEGER, trigger_id INTEGER, execution_fence"
                            + " INTEGER, status TEXT, update_time TEXT)");
            sql.execute(
                    "CREATE TABLE ifj_trigger_work_item(trigger_id INTEGER, status TEXT,"
                            + " claim_token TEXT, fence_token INTEGER, lease_deadline TEXT)");
            sql.execute("INSERT INTO ifj_instance VALUES(1,2,7,'RUNNING',NULL)");
            sql.execute(
                    "INSERT INTO ifj_trigger_work_item"
                            + " VALUES(2,'CLAIMED','claim',7,strftime('%Y-%m-%d %H:%M:%f','now','+1"
                            + " minute'))");
            assertEquals(0, execute(config, connection, "other"));
            assertEquals(1, execute(config, connection, "claim"));
            sql.execute("UPDATE ifj_instance SET status='RUNNING'");
            sql.execute(
                    "UPDATE ifj_trigger_work_item SET lease_deadline=strftime('%Y-%m-%d"
                            + " %H:%M:%f','now','-1 minute')");
            assertEquals(0, execute(config, connection, "claim"));
        }
    }

    private int execute(Configuration config, Connection connection, String claim)
            throws Exception {
        var statement = config.getMappedStatement(UPDATE);
        var values = parameters(claim);
        var bound = statement.getBoundSql(values);
        try (var sql = connection.prepareStatement(bound.getSql())) {
            new DefaultParameterHandler(statement, values, bound).setParameters(sql);
            return sql.executeUpdate();
        }
    }

    @ParameterizedTest
    @CsvSource({"TASK", "JOB"})
    void sqliteOptionalTimeoutRemainsNullAndPositiveTimeoutGetsDeadline(String target)
            throws Exception {
        var config = configuration("sqlite");
        var statement =
                config.getMappedStatement(
                        "com.ifielsc.ijob.infra.mapper.runtime.InstanceMapper.markRunningIfWaiting");
        var values =
                Map.of(
                        "id",
                        1L,
                        "startTime",
                        java.time.LocalDateTime.now(),
                        "permit",
                        ExecutionFencePermit.unfenced());
        var bound = statement.getBoundSql(values);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TABLE ifj_instance(id INTEGER, status TEXT, start_time TEXT, timeout_at"
                        + " TEXT, parent_instance_id INTEGER, target_type TEXT, task_definition_id"
                        + " INTEGER, definition_id INTEGER, update_time TEXT, message TEXT,"
                        + " error_code TEXT, message_key TEXT, message_args TEXT, wait_reason TEXT,"
                        + " permit_wait_deadline TEXT)");
            sql.execute("CREATE TABLE ifj_task_definition(id INTEGER, timeout INTEGER)");
            sql.execute("CREATE TABLE ifj_job_definition(id INTEGER, timeout INTEGER)");
            sql.execute("INSERT INTO ifj_task_definition VALUES(2,NULL)");
            sql.execute("INSERT INTO ifj_job_definition VALUES(2,NULL)");
            sql.execute(
                    "INSERT INTO"
                        + " ifj_instance(id,status,start_time,timeout_at,parent_instance_id,target_type,task_definition_id,definition_id,update_time)"
                        + " VALUES(1,'WAITING',NULL,NULL,NULL,'"
                            + target
                            + "',2,2,NULL)");
            for (boolean limited : new boolean[] {false, true}) {
                try (var update = connection.prepareStatement(bound.getSql())) {
                    new DefaultParameterHandler(statement, values, bound).setParameters(update);
                    assertEquals(1, update.executeUpdate());
                }
                try (var result =
                        sql.executeQuery(
                                "SELECT timeout_at IS NULL, timeout_at > strftime('%Y-%m-%d"
                                        + " %H:%M:%f','now','+59 seconds') FROM ifj_instance")) {
                    assertTrue(result.next());
                    assertEquals(!limited, result.getBoolean(1));
                    if (limited) assertTrue(result.getBoolean(2));
                }
                sql.execute("UPDATE ifj_task_definition SET timeout=60");
                sql.execute("UPDATE ifj_job_definition SET timeout=60");
                sql.execute("UPDATE ifj_instance SET status='WAITING'");
            }
        }
    }

    private Map<String, Object> parameters(String claim) {
        return Map.of(
                "id",
                1L,
                "permit",
                new ExecutionFencePermit(ExecutionFencePermit.Mode.CLAIMED, claim, 7L));
    }

    private Configuration configuration(String dialect) throws Exception {
        Configuration config = new Configuration();
        config.setDatabaseId(dialect);
        try (var input = new ClassPathResource(MAPPER).getInputStream()) {
            new XMLMapperBuilder(input, config, MAPPER, config.getSqlFragments()).parse();
        }
        return config;
    }
}
