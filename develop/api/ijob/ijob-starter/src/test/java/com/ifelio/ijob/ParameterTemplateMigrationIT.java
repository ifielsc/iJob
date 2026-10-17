package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.contract.enums.ScheduleType;
import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.parameter.ParameterTemplateQuery;
import com.ifelio.ijob.infra.entity.parameter.ParameterTemplate;
import com.ifelio.ijob.infra.entity.schedule.Schedule;
import com.ifelio.ijob.infra.mapper.parameter.ParameterTemplateMapper;
import com.ifelio.ijob.infra.mapper.schedule.ScheduleMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

class ParameterTemplateMigrationIT {
    @TempDir Path directory;

    @Test
    void sqliteUpgradeAndMapperRoundTrip() throws Exception {
        verify(
                new DriverManagerDataSource(
                        "jdbc:sqlite:" + directory.resolve("parameters.db") + "?foreign_keys=on"),
                "sqlite",
                null);
    }

    @Test
    void isolatedServerUpgradeAndMapperRoundTrip() throws Exception {
        String vendor = System.getenv("IJOB_PARAMETER_VENDOR"),
                target = System.getenv("IJOB_PARAMETER_TARGET");
        Assumptions.assumeTrue(vendor != null);
        assertTrue(Set.of("mysql", "postgresql", "sqlserver", "oracle").contains(vendor));
        assertTrue(target != null && target.matches("(?i)ijob_param36_[a-z0-9_]+"));
        var ds =
                new DriverManagerDataSource(
                        System.getenv("IJOB_PARAMETER_URL"),
                        System.getenv("IJOB_PARAMETER_USER"),
                        System.getenv("IJOB_PARAMETER_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        String identity =
                jdbc.queryForObject(
                        switch (vendor) {
                            case "mysql" -> "SELECT DATABASE()";
                            case "postgresql" -> "SELECT current_database()";
                            case "sqlserver" -> "SELECT DB_NAME()";
                            default -> "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM dual";
                        },
                        String.class);
        assertEquals(target.toLowerCase(), identity.toLowerCase());
        String count =
                switch (vendor) {
                    case "mysql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE()";
                    case "postgresql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                    + " table_schema='public'";
                    case "sqlserver" -> "SELECT COUNT(*) FROM sys.tables";
                    default -> "SELECT COUNT(*) FROM user_tables";
                };
        assertEquals(
                0L,
                jdbc.queryForObject(count, Long.class),
                "Only an empty dedicated database may be used");
        verify(ds, vendor, System.getenv("IJOB_PARAMETER_USER"));
    }

    private void verify(DriverManagerDataSource ds, String vendor, String user) throws Exception {
        var config =
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration/" + vendor)
                        .table("ifj_schema_history")
                        .cleanDisabled(true);
        if (vendor.equals("oracle")) config.defaultSchema(user);
        config.target("35").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO ifj_calendar(id,name,workdays_mask) VALUES(777,'preserved',31)");
        config.target("36").load().migrate();
        assertEquals(
                777L,
                jdbc.queryForObject(
                        "SELECT id FROM ifj_calendar WHERE name='preserved'", Long.class));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_parameter_template'",
                        Long.class));
        try (var conn = ds.getConnection()) {
            TableIdSchemaVerifier.verify(conn);
        }
        var c = new Configuration(new Environment("parameters", new JdbcTransactionFactory(), ds));
        c.setDatabaseId(vendor);
        c.setMapUnderscoreToCamelCase(true);
        c.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
        if (vendor.equals("sqlite"))
            c.getTypeHandlerRegistry()
                    .register(
                            java.time.LocalDateTime.class,
                            new com.ifelio.ijob.framework.database.typehandler
                                    .SqliteLocalDateTimeTypeHandler());
        for (String resource :
                new String[] {
                    "mapper/parameter/ParameterTemplateMapper.xml",
                    "mapper/schedule/ScheduleMapper.xml"
                }) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, c, resource, c.getSqlFragments()).parse();
            }
        }
        try (var session = new SqlSessionFactoryBuilder().build(c).openSession(true)) {
            var mapper = session.getMapper(ParameterTemplateMapper.class);
            var row = new ParameterTemplate();
            row.setId(1L);
            row.setName("Daily 日期");
            row.setRevision(1L);
            row.setFieldsJson("[{\"name\":\"batch\",\"type\":\"INTEGER\",\"defaultValue\":1000}]");
            assertEquals(1, mapper.insert(row));
            assertEquals(row.getFieldsJson(), mapper.selectById(1L).getFieldsJson());
            assertEquals(1, mapper.selectList(new ParameterTemplateQuery()).size());
            assertEquals(0, mapper.countTarget("TASK", 900L));
            assertEquals(1, mapper.insertBinding("TASK", 900L, 1L));
            assertEquals(1L, mapper.selectBinding("TASK", 900L));
            var schedules = session.getMapper(ScheduleMapper.class);
            var schedule = new Schedule();
            schedule.setId(1L);
            schedule.setTargetType(TargetType.TASK);
            schedule.setTargetId(900L);
            schedule.setScheduleType(ScheduleType.INTERVAL);
            schedule.setIntervalSec(60);
            schedule.setEnabled(false);
            schedule.setCreateTime(LocalDateTime.now());
            schedule.setUpdateTime(LocalDateTime.now());
            schedule.setVariables("{\"batch\":1000}");
            schedule.setParameterForm("{\"templateId\":1,\"fields\":[]}");
            assertEquals(1, schedules.insert(schedule));
            assertEquals(schedule.getParameterForm(), schedules.selectById(1L).getParameterForm());
            row.setFieldsJson("[]");
            assertEquals(1, mapper.updateById(row));
            assertEquals(0, mapper.updateById(row));
            assertEquals("{\"batch\":1000}", schedules.selectById(1L).getVariables());
            assertEquals(1, mapper.deleteById(1L));
            assertNull(mapper.selectBinding("TASK", 900L));
            assertEquals(schedule.getParameterForm(), schedules.selectById(1L).getParameterForm());
            schedule = schedules.selectById(1L);
            schedule.setVariables("{\"batch\":2000}");
            assertEquals(1, schedules.updateById(schedule));
            assertEquals("{\"batch\":2000}", schedules.selectById(1L).getVariables());
        }
        assertEquals(0, config.target("36").load().migrate().migrationsExecuted);
    }
}
