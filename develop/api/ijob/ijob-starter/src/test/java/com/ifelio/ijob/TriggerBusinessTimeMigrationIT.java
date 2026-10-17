package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.contract.enums.TargetType;
import com.ifelio.ijob.contract.enums.TriggerStatus;
import com.ifelio.ijob.contract.enums.TriggerType;
import com.ifelio.ijob.framework.database.typehandler.SqliteLocalDateTimeTypeHandler;
import com.ifelio.ijob.infra.entity.runtime.Trigger;
import com.ifelio.ijob.infra.mapper.runtime.TriggerMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;

class TriggerBusinessTimeMigrationIT {
    @TempDir Path directory;

    @Test
    void upgradesLegacyTriggersAndPersistsBusinessSnapshotThroughMapper() throws Exception {
        var ds =
                new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("business-time.db"));
        var flyway =
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration/sqlite")
                        .table("ifj_schema_history")
                        .cleanDisabled(true);
        flyway.target("36").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        jdbc.update(
                "INSERT INTO"
                    + " ifj_trigger(id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES(1,'TASK',1,'MANUAL','legacy','SUCCESS','2026-10-10 16:30:00.000')");
        flyway.target("37").load().migrate();
        // V37 is a historical fixture, not a release V1 installation.
        assertThrows(
                IllegalStateException.class,
                () -> new SqliteSchemaCompatibilityInspector(ds).verify());
        var config =
                new Configuration(
                        new Environment("business-time", new JdbcTransactionFactory(), ds));
        config.setDatabaseId("sqlite");
        config.setJdbcTypeForNull(org.apache.ibatis.type.JdbcType.NULL);
        config.getTypeHandlerRegistry()
                .register(LocalDateTime.class, SqliteLocalDateTimeTypeHandler.class);
        try (var input = getClass().getResourceAsStream("/mapper/runtime/TriggerMapper.xml")) {
            new XMLMapperBuilder(input, config, "TriggerMapper.xml", config.getSqlFragments())
                    .parse();
        }
        var factory = new SqlSessionFactoryBuilder().build(config);
        try (var session = factory.openSession(true)) {
            TriggerMapper mapper = session.getMapper(TriggerMapper.class);
            assertNull(mapper.selectById(1L).getBusinessTime());
            Trigger trigger = new Trigger();
            trigger.setId(2L);
            trigger.setTargetType(TargetType.TASK);
            trigger.setTargetId(1L);
            trigger.setTriggerType(TriggerType.MANUAL);
            trigger.setIdempotencyKey("new");
            trigger.setStatus(TriggerStatus.WAITING);
            trigger.setTriggerTime(LocalDateTime.parse("2026-10-10T16:30:00"));
            trigger.setBusinessDate("2026-10-11");
            trigger.setBusinessTime("2026-10-11T00:30:00+08:00");
            trigger.setBusinessZoneId("Asia/Shanghai");
            assertEquals(1, mapper.insert(trigger));
        }
        try (var session = factory.openSession()) {
            Trigger restored = session.getMapper(TriggerMapper.class).selectById(2L);
            assertEquals("2026-10-11T00:30:00+08:00", restored.getBusinessTime());
            assertEquals("2026-10-11", restored.getBusinessDate());
            assertEquals("Asia/Shanghai", restored.getBusinessZoneId());
            assertEquals(LocalDateTime.parse("2026-10-10T16:30:00"), restored.getTriggerTime());
        }
        assertEquals(0, flyway.target("37").load().migrate().migrationsExecuted);
    }
}
