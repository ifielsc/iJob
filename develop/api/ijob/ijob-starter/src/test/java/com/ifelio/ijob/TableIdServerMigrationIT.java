package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.infra.coordination.id.DatabaseIdSegmentProvider;
import com.ifelio.ijob.infra.coordination.id.DatabaseSegmentIdGenerator;
import com.ifelio.ijob.infra.coordination.id.IdNamespace;
import com.ifelio.ijob.infra.mapper.coordination.IdSegmentMapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class TableIdServerMigrationIT {
    @Test
    void dedicatedDatabaseShouldUpgradeAndAllocateAcrossRealConnections() throws Exception {
        String vendor = System.getenv("IJOB_TABLE_ID_VENDOR");
        String target = System.getenv("IJOB_TABLE_ID_TARGET");
        String url = System.getenv("IJOB_TABLE_ID_URL");
        String user = System.getenv("IJOB_TABLE_ID_USER");
        assertTrue(Set.of("mysql", "postgresql", "sqlserver", "oracle").contains(vendor));
        assertTrue(target != null && target.matches("(?i)ijob_id35_[a-z0-9_]+"));
        var ds = new DriverManagerDataSource(url, user, System.getenv("IJOB_TABLE_ID_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        String identity =
                switch (vendor) {
                    case "mysql" -> jdbc.queryForObject("SELECT DATABASE()", String.class);
                    case "postgresql" ->
                            jdbc.queryForObject("SELECT current_database()", String.class);
                    case "sqlserver" -> jdbc.queryForObject("SELECT DB_NAME()", String.class);
                    default ->
                            jdbc.queryForObject(
                                    "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM dual",
                                    String.class);
                };
        assertEquals(target.toLowerCase(), identity.toLowerCase());
        String tableCount =
                switch (vendor) {
                    case "mysql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                    + " table_schema=DATABASE() AND table_name LIKE 'ifj_%'";
                    case "postgresql" ->
                            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                                    + " table_schema='public' AND table_name LIKE 'ifj_%'";
                    case "sqlserver" -> "SELECT COUNT(*) FROM sys.tables WHERE name LIKE 'ifj_%'";
                    default -> "SELECT COUNT(*) FROM user_tables WHERE table_name LIKE 'IFJ_%'";
                };
        assertEquals(
                0L,
                jdbc.queryForObject(tableCount, Long.class),
                "Only an empty dedicated database may be migrated");
        var configuration =
                Flyway.configure()
                        .dataSource(ds)
                        .locations("classpath:db/migration/" + vendor)
                        .table("ifj_schema_history")
                        .cleanDisabled(true);
        if (vendor.equals("oracle")) configuration.defaultSchema(user);
        configuration.target("33").load().migrate();
        assertEquals(
                vendor.equals("oracle") ? 47L : 48L, jdbc.queryForObject(tableCount, Long.class));
        jdbc.update("INSERT INTO ifj_calendar (id,name,workdays_mask) VALUES (77,'existing',31)");
        jdbc.update(
                "UPDATE ifj_id_segment SET next_value=9000 WHERE"
                        + " segment_key='platform-primary-id'");
        configuration.target("35").load().migrate();
        assertEquals(
                vendor.equals("oracle") ? 44L : 45L, jdbc.queryForObject(tableCount, Long.class));
        assertEquals(
                27L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_id_segment WHERE segment_key LIKE 'pk:%' AND"
                                + " next_value=9000",
                        Long.class));
        assertEquals(
                77L,
                jdbc.queryForObject(
                        "SELECT id FROM ifj_calendar WHERE name='existing'", Long.class));
        configuration.target("37").load().migrate();
        switch (vendor) {
            case "mysql" -> new MySqlSchemaCompatibilityInspector(ds).verify();
            case "postgresql" -> new PostgreSqlSchemaCompatibilityInspector(ds).verify();
            case "sqlserver" -> new SqlServerSchemaCompatibilityInspector(ds).verify();
            default -> new OracleSchemaCompatibilityInspector(ds).verify();
        }
        var c =
                new Configuration(
                        new Environment("server-table-id", new JdbcTransactionFactory(), ds));
        c.setDatabaseId(vendor);
        c.setMapUnderscoreToCamelCase(true);
        c.setDefaultStatementTimeout(20);
        String resource = "mapper/coordination/IdSegmentMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
        }
        var sessions = new SqlSessionFactoryBuilder().build(c);
        com.ifelio.ijob.infra.coordination.id.IdSegmentProvider provider =
                (key, size) -> {
                    try (var session = sessions.openSession(false)) {
                        var range =
                                new DatabaseIdSegmentProvider(
                                                session.getMapper(IdSegmentMapper.class))
                                        .reserve(key, size);
                        session.commit();
                        return range;
                    }
                };
        var first = new DatabaseSegmentIdGenerator(provider, new AppProperties());
        var second = new DatabaseSegmentIdGenerator(provider, new AppProperties());
        assertEquals(9000, first.nextId(IdNamespace.CALENDAR));
        assertEquals(9000, second.nextId(IdNamespace.TASK));
        var ids = java.util.concurrent.ConcurrentHashMap.<Long>newKeySet();
        var tasks = new ArrayList<Future<?>>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int thread = 0; thread < 4; thread++) {
                var generator = thread % 2 == 0 ? first : second;
                tasks.add(
                        executor.submit(
                                () -> {
                                    for (int i = 0; i < 50; i++)
                                        assertTrue(
                                                ids.add(
                                                        generator.nextId(
                                                                IdNamespace.INSTANCE_LOG)));
                                }));
            }
            for (var task : tasks) task.get();
        }
        assertEquals(200, ids.size());
        assertEquals(9001, second.nextId(IdNamespace.CALENDAR));
        assertThrows(IllegalStateException.class, () -> provider.reserve("platform-primary-id", 1));
        jdbc.update("DELETE FROM ifj_id_segment WHERE segment_key='pk:ifj_resource'");
        assertThrows(IllegalStateException.class, () -> first.nextId(IdNamespace.RESOURCE));
        jdbc.update(
                "INSERT INTO ifj_id_segment (segment_key,next_value,segment_size,update_time)"
                        + " SELECT 'pk:ifj_resource',9000,1,update_time FROM ifj_id_segment WHERE"
                        + " segment_key='pk:ifj_calendar'");
        try (var session = sessions.openSession(false)) {
            new DatabaseIdSegmentProvider(session.getMapper(IdSegmentMapper.class))
                    .reserve("pk:ifj_job", 1);
            session.rollback();
        }
        assertEquals(9000, first.nextId(IdNamespace.JOB));
    }
}
