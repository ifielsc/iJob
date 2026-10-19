package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.etl.EtlCheckpointStore;
import com.ifielsc.ijob.core.etl.EtlFileStateStore;
import com.ifielsc.ijob.core.execute.ExecutionHandle;
import com.ifielsc.ijob.core.execute.task.TaskContext;
import com.ifielsc.ijob.core.execute.variable.VariableResolver;
import com.ifielsc.ijob.core.resource.ResourceClientLease;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.factory.file.client.LocalFileClient;
import com.ifielsc.ijob.plugin.etl.EtlPipelineFactory;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.etl.extractor.EtlExtractorFactory;
import com.ifielsc.ijob.plugin.etl.incremental.EtlFileIncrementalRunner;
import com.ifielsc.ijob.plugin.etl.loader.EtlLoaderFactory;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.sql.DriverDataSource;
import com.ifielsc.ijob.plugin.etl.transformer.TransformerFactory;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EtlFileIncrementalIT {
    @TempDir static Path directory;
    private ServletWebServerApplicationContext app;
    private JdbcTemplate platform;
    private final AtomicLong ids = new AtomicLong(10000);

    @BeforeAll
    void start() {
        app =
                (ServletWebServerApplicationContext)
                        new SpringApplicationBuilder(IJobApplication.class)
                                .registerShutdownHook(false)
                                .run(
                                        "--server.address=127.0.0.1",
                                        "--server.port=0",
                                        "--spring.config.import=optional:classpath:database/sqlite.yml",
                                        "--spring.datasource.url=jdbc:sqlite:"
                                                + directory.resolve("platform.db")
                                                + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE",
                                        "--spring.flyway.enabled=true",
                                        "--spring.flyway.locations=classpath:db/release/sqlite",
                                        "--app.platform-database.vendor=SQLITE",
                                        "--app.runtime.mode=STANDALONE",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=etl-test",
                                        "--app.security.aes.key="
                                                + Base64.getEncoder().encodeToString(new byte[32]),
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=etl-test",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4)
                                                        .encode("Etl-fixture-password-123"),
                                        "--app.schedule.enabled=false",
                                        "--app.event.recovery.enabled=false",
                                        "--app.event.ingress.enabled=false",
                                        "--app.notification.outbox.enabled=false",
                                        "--app.plugin.etl.enabled=true",
                                        "--app.retention.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--logging.level.root=OFF");
        platform = new JdbcTemplate(app.getBean(DataSource.class));
    }

    @AfterAll
    void stop() {
        if (app != null) app.close();
    }

    @Test
    void automaticMappingAcrossFilesOverridesAndExcludesWithoutChangingCheckpointSemantics()
            throws Exception {
        var f = fixture();
        var target = f.config.getTarget();
        target.setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        target.setColumns(Map.of("value", "description"));
        target.setExcludedFields(List.of("private_note"));
        file(f, "orders_1_a.csv", "id,description,private_note\n1,one,secret\n");
        file(f, "orders_2_a.csv", "id,description,private_note\n2,two,secret\n");
        var context = run(f);
        assertEquals(2L, context.getVariable("etl.completedFiles"));
        assertEquals(2, count(f));
        assertEquals("2", watermark(f));
        assertEquals(
                Map.of("id", "id", "value", "description"),
                context.getVariable("etl.resolvedColumns"));
        assertEquals(
                "two",
                f.target.queryForObject(
                        "select \"value\" from \"orders\" where \"id\"='2'", String.class));
        assertEquals(0L, run(f).getVariable("etl.completedFiles"));
    }

    @Test
    void automaticMappingRejectsLaterFileFieldDriftWithoutAdvancingItsCheckpoint()
            throws Exception {
        var f = fixture();
        f.config.getTarget().setMappingMode(EtlTaskConfig.MappingMode.AUTO);
        f.config.getTarget().setColumns(Map.of());
        file(f, "orders_1_a.csv", "id,value\n1,one\n");
        file(f, "orders_2_a.csv", "id,value,new_field\n2,two,unexpected\n");
        assertThrows(RuntimeException.class, () -> run(f));
        assertEquals(1, count(f));
        assertEquals("1", watermark(f));
        file(f, "orders_2_a.csv", "id,value\n2,two\n");
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        assertEquals(2, count(f));
        assertEquals("2", watermark(f));
    }

    @Test
    void numericOrderEqualMarkersLookbackAndChangedFiles() throws Exception {
        var f = fixture();
        file(f, "orders_10_a.csv", "id,value\n1,new\n");
        file(f, "orders_9_a.csv", "id,value\n1,old\n");
        var c = run(f);
        assertEquals(
                "new",
                f.target.queryForObject(
                        "select \"value\" from \"orders\" where \"id\"='1'", String.class));
        assertEquals(2L, c.getVariable("etl.completedFiles"));
        file(f, "orders_10_b.csv", "id,value\n2,same-marker\n");
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        file(f, "orders_9_late.csv", "id,value\n3,late\n");
        f.config.getIncremental().getFile().setSelectionMode(EtlTaskConfig.SelectionMode.LOOKBACK);
        f.config.getIncremental().getFile().setLookback("2");
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        assertEquals("10", watermark(f));
        file(f, "orders_10_b.csv", "id,value\n2,changed\n");
        assertThrows(IllegalArgumentException.class, () -> run(f));
        f.config
                .getIncremental()
                .getFile()
                .setChangedFilePolicy(EtlTaskConfig.ChangedFilePolicy.REPROCESS);
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        assertEquals(3, count(f));
    }

    @Test
    void failureDoesNotSkipFileAndRetryRetainsCompletedFiles() throws Exception {
        var f = fixture();
        file(f, "orders_1_a.csv", "id,value\n1,one\n");
        file(f, "orders_2_a.csv", "id,value\n2,two,unexpected\n");
        file(f, "orders_3_a.csv", "id,value\n3,three\n");
        assertThrows(RuntimeException.class, () -> run(f));
        assertEquals("1", watermark(f));
        assertEquals(1, count(f));
        file(f, "orders_2_a.csv", "id,value\n2,two\n");
        assertEquals(2L, run(f).getVariable("etl.completedFiles"));
        assertEquals(3, count(f));
        assertEquals("3", watermark(f));
    }

    @Test
    void readyBarrierAndQuotasRunBeforeTargetWrites() throws Exception {
        var f = fixture();
        f.config.getIncremental().getFile().setReadySuffix(".done");
        file(f, "orders_1_a.csv", "id,value\n1,one\n");
        file(f, "orders_2_a.csv", "id,value\n2,two\n");
        file(f, "orders_2_a.csv.done", "");
        assertEquals(0L, run(f).getVariable("etl.completedFiles"));
        assertEquals(0, count(f));
        file(f, "orders_1_a.csv.done", "");
        f.config.getIncremental().getFile().setMaxFiles(1);
        assertThrows(IllegalArgumentException.class, () -> run(f));
        assertEquals(0, count(f));
        f.config.getIncremental().getFile().setMaxFiles(100);
        assertEquals(2L, run(f).getVariable("etl.completedFiles"));
    }

    @Test
    void stateOwnershipAndCompletionAreTransactional() throws Exception {
        var f = fixture();
        var store = app.getBean(EtlCheckpointStore.class);
        var state = app.getBean(EtlFileStateStore.class);
        var initial =
                store.loadOrCreate(f.instance, "lock", "a".repeat(64), "LONG", "0", List.of());
        var owner = state.acquire(initial, f.instance, "first");
        assertThrows(RuntimeException.class, () -> state.acquire(owner, f.instance, "second"));
        assertThrows(
                RuntimeException.class,
                () ->
                        state.complete(
                                owner,
                                f.instance,
                                "wrong",
                                "b".repeat(64),
                                "c".repeat(64),
                                "1",
                                "1"));
        assertEquals(
                0,
                platform.queryForObject(
                        "SELECT count(*) FROM ifj_etl_file_record WHERE task_id=?",
                        Integer.class,
                        f.task));
        var finished =
                state.complete(
                        owner, f.instance, "first", "b".repeat(64), "c".repeat(64), "1", "1");
        assertEquals("c".repeat(64), state.digest(finished, "b".repeat(64)));
        state.release(finished, "first");
        var next = state.acquire(finished, f.instance, "next");
        state.release(next, "next");
    }

    @Test
    void fileSqlUsesTheSameStagedSnapshot() throws Exception {
        var f = fixture();
        f.config.getSource().setReadMode(EtlTaskConfig.FileReadMode.SQL);
        f.config.getSource().setSql("SELECT * FROM \"SOURCE_DATA\"");
        file(f, "orders_9007199254740993_a.csv", "id,value\n1,precise\n");
        run(f);
        assertEquals("9007199254740993", watermark(f));
        assertEquals(1, count(f));
    }

    @Test
    void abandonedOwnerCanRecoverButExpiredFenceCannotComplete() throws Exception {
        var first = fixture();
        var second = fixture();
        platform.update(
                "UPDATE ifj_instance SET target_id=? WHERE id=?", first.task, second.instance);
        var checkpoints = app.getBean(EtlCheckpointStore.class);
        var state = app.getBean(EtlFileStateStore.class);
        var initial =
                checkpoints.loadOrCreate(
                        first.instance, "recovery", "d".repeat(64), "LONG", "0", List.of());
        var held = state.acquire(initial, first.instance, "first");
        var observed =
                checkpoints.loadOrCreate(
                        second.instance, "recovery", "d".repeat(64), "LONG", "0", List.of());
        assertThrows(
                RuntimeException.class, () -> state.acquire(observed, second.instance, "second"));
        platform.update("UPDATE ifj_instance SET status='FAILED' WHERE id=?", first.instance);
        var recovered = state.acquire(observed, second.instance, "second");
        assertThrows(RuntimeException.class, () -> state.verify(held, first.instance, "first"));
        state.release(held, "first");
        platform.update(
                "UPDATE ifj_trigger_work_item SET lease_deadline='2000-01-01 00:00:00.000' WHERE"
                        + " trigger_id=?",
                second.instance);
        assertThrows(
                RuntimeException.class,
                () ->
                        state.complete(
                                recovered,
                                second.instance,
                                "second",
                                "a".repeat(64),
                                "b".repeat(64),
                                "1",
                                "1"));
        assertEquals(
                0,
                platform.queryForObject(
                        "SELECT count(*) FROM ifj_etl_file_record WHERE task_id=?",
                        Integer.class,
                        first.task));
        assertEquals(
                "\"0\"",
                platform.queryForObject(
                        "SELECT watermark_value FROM ifj_etl_checkpoint WHERE task_id=? AND"
                                + " checkpoint_key='recovery'",
                        String.class,
                        first.task));
        state.release(recovered, "second");
    }

    @Test
    void recursiveDatePartitionsAndExplicitUpperBound() throws Exception {
        var f = fixture();
        var incremental = f.config.getIncremental();
        incremental.setWatermarkType("DATE");
        incremental.setInitialWatermark("2026-09-25");
        var selection = incremental.getFile();
        selection.setRecursive(true);
        selection.setMarkerLocation(EtlTaskConfig.MarkerLocation.RELATIVE_PATH);
        selection.setExpression("{mark}/orders.csv");
        selection.setMarkerFormat("yyyy/MM/dd");
        selection.setUpperBound("2026-09-27");
        Files.createDirectories(f.files.resolve("2026/09/26"));
        Files.createDirectories(f.files.resolve("2026/09/28"));
        file(f, "2026/09/26/orders.csv", "id,value\n1,partition\n");
        file(f, "2026/09/28/orders.csv", "id,value\n2,future\n");
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        assertEquals("2026-09-26", watermark(f));
        selection.setUpperBound("2026-09-28");
        assertEquals(1L, run(f).getVariable("etl.completedFiles"));
        assertEquals(2, count(f));
    }

    @Test
    void unsafeCombinationsAndChangedSourceFailBeforeWriting() throws Exception {
        var f = fixture();
        file(f, "orders_1_a.csv", "id,value\n1,one\n");
        run(f);
        f.config.getTarget().getBeforeSql().add("DELETE FROM orders");
        assertThrows(IllegalArgumentException.class, () -> run(f));
        f.config.getTarget().getBeforeSql().clear();
        f.config.getTarget().setMode(EtlTaskConfig.LoadMode.APPEND);
        assertThrows(IllegalArgumentException.class, () -> run(f));
        f.config.getTarget().setMode(EtlTaskConfig.LoadMode.UPSERT);
        f.config.getIncremental().getFile().setExpression("other_{mark}.csv");
        assertThrows(RuntimeException.class, () -> run(f));
        assertEquals(1, count(f));
        assertEquals("1", watermark(f));
    }

    @Test
    void publicationChecksReferencesWithoutReadingFilesOrOpeningTarget() throws Exception {
        var f = fixture();
        platform.update("UPDATE ifj_auth_user SET must_change_password=0");
        var login =
                request(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", "etl-test", "password", "Etl-fixture-password-123"),
                        null);
        assertEquals(200, login.statusCode());
        var json = new ObjectMapper();
        String token = json.readTree(login.body()).path("data").path("tokenValue").asText();
        var source =
                request(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                "Source",
                                "code",
                                "etl-source",
                                "type",
                                "FILE",
                                "enabled",
                                true,
                                "fileConfig",
                                Map.of("protocol", "LOCAL", "basePath", f.files.toString())),
                        token);
        var target =
                request(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                "Target",
                                "code",
                                "etl-target",
                                "type",
                                "DB",
                                "enabled",
                                true,
                                "dbConfig",
                                Map.of(
                                        "dbType",
                                        "MYSQL",
                                        "jdbcUrl",
                                        "jdbc:mysql://127.0.0.1:1/fixture",
                                        "username",
                                        "fixture")),
                        token);
        assertEquals(201, source.statusCode(), source.body());
        assertEquals(201, target.statusCode(), target.body());
        long sourceId = json.readTree(source.body()).path("data").asLong();
        long targetId = json.readTree(target.body()).path("data").asLong();
        f.config.getSource().setResourceId(sourceId);
        f.config.getTarget().setResourceId(targetId);
        var valid =
                request(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "ETL publication",
                                "pluginType",
                                "ETL",
                                "enabled",
                                true,
                                "pluginConfig",
                                json.writeValueAsString(f.config)),
                        token);
        assertEquals(201, valid.statusCode(), valid.body());
        assertEquals(
                409, request("DELETE", "/api/resources/" + sourceId, null, token).statusCode());
        f.config.getSource().setResourceId(targetId);
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "Wrong source",
                                        "pluginType",
                                        "ETL",
                                        "enabled",
                                        true,
                                        "pluginConfig",
                                        json.writeValueAsString(f.config)),
                                token)
                        .statusCode());
        f.config.getSource().setResourceId(sourceId);
        platform.update("UPDATE ifj_resource SET enabled=0 WHERE id=?", sourceId);
        assertEquals(
                400,
                request(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "Disabled source",
                                        "pluginType",
                                        "ETL",
                                        "enabled",
                                        true,
                                        "pluginConfig",
                                        json.writeValueAsString(f.config)),
                                token)
                        .statusCode());
        assertEquals(0, count(f));
        assertEquals(
                0,
                platform.queryForObject(
                        "SELECT count(*) FROM ifj_etl_checkpoint WHERE task_id=?",
                        Integer.class,
                        f.task));
    }

    private java.net.http.HttpResponse<String> request(
            String method, String path, Object body, String token) throws Exception {
        var request =
                java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create(
                                        "http://127.0.0.1:" + app.getWebServer().getPort() + path))
                        .header("Content-Type", "application/json");
        if (token != null) request.header("ijob-token", token);
        request.method(
                method,
                body == null
                        ? java.net.http.HttpRequest.BodyPublishers.noBody()
                        : java.net.http.HttpRequest.BodyPublishers.ofString(
                                new ObjectMapper().writeValueAsString(body)));
        return java.net.http.HttpClient.newHttpClient()
                .send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private Fixture fixture() throws Exception {
        long task = ids.incrementAndGet(), instance = ids.incrementAndGet();
        String claim = UUID.randomUUID().toString();
        platform.update(
                "INSERT INTO"
                    + " ifj_trigger(id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time)"
                    + " VALUES(?,'TASK',?,'MANUAL',?,'RUNNING',strftime('%Y-%m-%d"
                    + " %H:%M:%f','now'))",
                instance, task, claim);
        platform.update(
                "INSERT INTO"
                    + " ifj_instance(id,target_type,target_id,status,trigger_id,execution_fence)"
                    + " VALUES(?,'TASK',?,'RUNNING',?,1)",
                instance,
                task,
                instance);
        platform.update(
                "INSERT INTO"
                    + " ifj_trigger_work_item(trigger_id,status,available_at,lease_owner,claim_token,lease_deadline,fence_token,attempt_count,max_attempts)"
                    + " VALUES(?,'CLAIMED',strftime('%Y-%m-%d"
                    + " %H:%M:%f','now'),'etl-test',?,strftime('%Y-%m-%d %H:%M:%f','now','+1"
                    + " hour'),1,1,3)",
                instance, claim);
        app.getBean(com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator.class)
                .activate(
                        new com.ifielsc.ijob.infra.coordination.work.TriggerWorkClaim(
                                instance,
                                "etl-test",
                                claim,
                                1L,
                                1,
                                3,
                                java.time.LocalDateTime.now().plusHours(1)));
        Path files = Files.createDirectory(directory.resolve("source-" + task));
        DataSource target =
                new DriverDataSource(
                        "org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(target);
        jdbc.execute(
                "CREATE TABLE \"orders\"(\"id\" varchar(32) primary key, \"value\" varchar(128))");
        var resource = new FileResourceConfig();
        resource.setBasePath(files.toString());
        var resolver =
                new ResourceResolver(null) {
                    @Override
                    public ResourceClientLease<FileClient> leaseFileClient(Long id) {
                        return new ResourceClientLease<>(new LocalFileClient(resource), () -> {});
                    }

                    @Override
                    public DataSource getDataSource(Long id) {
                        return target;
                    }
                };
        var config = new EtlTaskConfig();
        var source = new EtlTaskConfig.SourceConfig();
        source.setType(EtlTaskConfig.SourceType.FILE);
        source.setResourceId(1L);
        source.setPath("/");
        source.setFormat(EtlTaskConfig.FileFormat.CSV);
        config.setSource(source);
        var destination = new EtlTaskConfig.TargetConfig();
        destination.setType(EtlTaskConfig.TargetType.DATABASE);
        destination.setResourceId(2L);
        destination.setTable("orders");
        destination.setColumns(Map.of("id", "id", "value", "value"));
        destination.setLogicalKeys(List.of("id"));
        destination.setMode(EtlTaskConfig.LoadMode.UPSERT);
        destination.setTransactionScope(EtlTaskConfig.TransactionScope.RUN);
        config.setTarget(destination);
        var increment = config.getIncremental();
        increment.setEnabled(true);
        increment.setCheckpointKey("orders");
        increment.setWatermarkType("LONG");
        increment.setInitialWatermark("0");
        increment.setAllowCheckpointPastInvalidRecords(false);
        increment.setFile(new EtlTaskConfig.FileIncrementalConfig());
        increment.getFile().setExpression("orders_{mark}_*.csv");
        increment.getFile().setStableAgeSeconds(0);
        var pipelines =
                new EtlPipelineFactory(
                        new EtlExtractorFactory(resolver),
                        new EtlLoaderFactory(resolver),
                        new TransformerFactory(List.of()),
                        new ValidatorFactory(List.of()));
        var runner =
                new EtlFileIncrementalRunner(
                        app.getBean(EtlCheckpointStore.class),
                        app.getBean(EtlFileStateStore.class),
                        resolver,
                        pipelines,
                        app.getBean(ObjectMapper.class));
        return new Fixture(task, instance, files, jdbc, config, runner);
    }

    private TaskContext run(Fixture f) throws Exception {
        new EtlTaskConfigValidator().validate(f.config);
        var context =
                new TaskContext(
                        f.instance,
                        new ExecutionHandle(f.instance),
                        null,
                        null,
                        new VariableResolver(),
                        Map.of()) {
                    @Override
                    public void progress(Integer value) {}

                    @Override
                    public void logInfo(String value) {}

                    @Override
                    public void logError(String value) {}
                };
        try {
            f.runner.execute(new EtlContext(context, f.config));
            return context;
        } finally {
            context.cleanupResources();
        }
    }

    private void file(Fixture f, String name, String body) throws Exception {
        Files.writeString(f.files.resolve(name), body);
    }

    private int count(Fixture f) {
        return f.target.queryForObject("SELECT count(*) FROM \"orders\"", Integer.class);
    }

    private String watermark(Fixture f) throws Exception {
        String raw =
                platform.queryForObject(
                        "SELECT watermark_value FROM ifj_etl_checkpoint WHERE task_id=? AND"
                                + " checkpoint_key='orders'",
                        String.class,
                        f.task);
        return new ObjectMapper().readValue(raw, String.class);
    }

    private record Fixture(
            long task,
            long instance,
            Path files,
            JdbcTemplate target,
            EtlTaskConfig config,
            EtlFileIncrementalRunner runner) {}
}
