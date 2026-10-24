// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobPackageHttpIT {
    @TempDir static Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final String password = UUID.randomUUID() + "aA1!";
    private Node source, target;
    private String resource, task, job, content, referenceContent, multiContent, planId;
    private final Map<String, byte[]> aesKeys = new java.util.HashMap<>();
    private JsonNode inspected, completed;
    private Map<String, Object> choices;

    record Node(
            ServletWebServerApplicationContext context,
            String base,
            String token,
            JdbcTemplate jdbc) {}

    @BeforeAll
    void start() throws Exception {
        source = startNode("source");
        resource = createResource(source, "source-db");
        task =
                call(source, "POST", "/api/tasks", task("Package SQL", resource, "SELECT 1"))
                        .path("taskId")
                        .asText();
        var node =
                Map.of(
                        "nodeId",
                        "read",
                        "name",
                        "Read",
                        "kind",
                        "EXECUTION",
                        "nodeType",
                        "TASK",
                        "refId",
                        task,
                        "policy",
                        Map.of("failStrategy", "STOP", "executeCondition", "ALL_SUCCESS"));
        job =
                call(
                                source,
                                "POST",
                                "/api/jobs",
                                Map.of(
                                        "name",
                                        "Package job",
                                        "enabled",
                                        true,
                                        "groups",
                                        List.of(),
                                        "nodes",
                                        List.of(node),
                                        "edges",
                                        List.of()))
                        .path("jobId")
                        .asText();
        var fields =
                List.of(
                        Map.of(
                                "name",
                                "region",
                                "type",
                                "STRING",
                                "required",
                                true,
                                "sensitive",
                                false));
        String template =
                call(
                                source,
                                "POST",
                                "/api/parameter-templates",
                                Map.of("name", "Package parameters", "fields", fields))
                        .asText();
        call(
                source,
                "PUT",
                "/api/parameter-templates/bindings/JOB/" + job,
                Map.of("templateId", template));
        String calendar =
                call(
                                source,
                                "POST",
                                "/api/calendars",
                                Map.of(
                                        "name",
                                        "Package calendar",
                                        "workdays",
                                        List.of("MONDAY", "TUESDAY"),
                                        "exceptions",
                                        List.of()))
                        .asText();
        call(
                source,
                "POST",
                "/api/schedules",
                Map.of(
                        "targetType",
                        "JOB",
                        "targetId",
                        job,
                        "scheduleType",
                        "INTERVAL",
                        "intervalSec",
                        60,
                        "enabled",
                        false,
                        "calendarId",
                        calendar,
                        "variables",
                        Map.of("region", "east"),
                        "parameterForm",
                        Map.of("templateId", template, "fields", fields)));
        content =
                call(
                                source,
                                "POST",
                                "/api/job-packages/export",
                                Map.of(
                                        "roots",
                                        List.of(Map.of("kind", "JOB", "id", job)),
                                        "includeResources",
                                        true,
                                        "includeSchedules",
                                        true))
                        .path("content")
                        .asText();
        referenceContent =
                call(
                                source,
                                "POST",
                                "/api/job-packages/export",
                                Map.of("roots", List.of(Map.of("kind", "TASK", "id", task))))
                        .path("content")
                        .asText();
        String second =
                call(
                                source,
                                "POST",
                                "/api/jobs",
                                Map.of(
                                        "name",
                                        "Second shared job",
                                        "enabled",
                                        false,
                                        "groups",
                                        List.of(),
                                        "nodes",
                                        List.of(node),
                                        "edges",
                                        List.of()))
                        .path("jobId")
                        .asText();
        multiContent =
                call(
                                source,
                                "POST",
                                "/api/job-packages/export",
                                Map.of(
                                        "roots",
                                        List.of(
                                                Map.of("kind", "JOB", "id", job),
                                                Map.of("kind", "JOB", "id", second),
                                                Map.of("kind", "TASK", "id", task)),
                                        "includeResources",
                                        true,
                                        "includeSchedules",
                                        true))
                        .path("content")
                        .asText();
        source.context().close();
        source = null;
        target = startNode("target");
        createResource(target, "unrelated");
    }

    private Node startNode(String name) throws Exception {
        byte[] aes =
                aesKeys.computeIfAbsent(
                        name,
                        k -> {
                            byte[] bytes = new byte[32];
                            new java.security.SecureRandom().nextBytes(bytes);
                            return bytes;
                        });
        var context =
                (ServletWebServerApplicationContext)
                        new SpringApplicationBuilder(IJobApplication.class)
                                .registerShutdownHook(false)
                                .run(
                                        "--server.address=127.0.0.1",
                                        "--server.port=0",
                                        "--spring.config.import=optional:classpath:database/sqlite.yml",
                                        "--spring.datasource.url=jdbc:sqlite:"
                                                + directory.resolve(name + ".db")
                                                + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE",
                                        "--spring.datasource.hikari.minimum-idle=0",
                                        "--spring.flyway.enabled=true",
                                        "--spring.flyway.locations=classpath:db/release/sqlite",
                                        "--app.platform-database.vendor=SQLITE",
                                        "--app.runtime.mode=STANDALONE",
                                        "--app.runtime.instance-name=package-" + name,
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=package-admin",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4).encode(password),
                                        "--app.security.aes.key-id=package-test",
                                        "--app.security.aes.key="
                                                + Base64.getEncoder().encodeToString(aes),
                                        "--app.schedule.enabled=false",
                                        "--app.event.recovery.enabled=false",
                                        "--app.event.ingress.enabled=false",
                                        "--app.notification.outbox.enabled=false",
                                        "--app.retention.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--logging.level.root=OFF");
        JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        Node node =
                new Node(
                        context,
                        "http://127.0.0.1:" + context.getWebServer().getPort(),
                        null,
                        jdbc);
        String token =
                call(
                                node,
                                "POST",
                                "/api/auth/login",
                                Map.of("username", "package-admin", "password", password))
                        .path("tokenValue")
                        .asText();
        return new Node(context, node.base(), token, jdbc);
    }

    @AfterAll
    void stop() {
        if (source != null) source.context().close();
        if (target != null) target.context().close();
    }

    private String createResource(Node node, String code) throws Exception {
        return call(
                        node,
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                code,
                                "code",
                                code,
                                "type",
                                "DB",
                                "enabled",
                                true,
                                "dbConfig",
                                Map.of(
                                        "dbType",
                                        "MYSQL",
                                        "jdbcUrl",
                                        "jdbc:mysql://127.0.0.1:1/package-fixture",
                                        "username",
                                        "fixture",
                                        "password",
                                        password)))
                .asText();
    }

    private Map<String, Object> task(String name, String id, String sql) throws Exception {
        return Map.of(
                "name",
                name,
                "pluginType",
                "SQL",
                "pluginConfig",
                json.writeValueAsString(Map.of("resourceId", id, "script", sql)),
                "enabled",
                false,
                "groups",
                List.of(),
                "retryTimes",
                0,
                "retryInterval",
                0);
    }

    @Test
    @Order(1)
    void exportCollectsDependenciesAndExcludesSecrets() throws Exception {
        inspected = call(target, "POST", "/api/job-packages/inspect", Map.of("content", content));
        assertEquals(7, inspected.path("objects").size());
        assertFalse(inspected.toString().contains(password));
        assertEquals(1, entry("RESOURCE").path("requiredSecrets").size());
        assertTrue(entry("TASK").path("body").path("pluginConfig").path("resourceId").isNull());
        assertFalse(entry("JOB").path("body").path("enabled").asBoolean());
        var multi =
                call(target, "POST", "/api/job-packages/inspect", Map.of("content", multiContent));
        assertEquals(3, multi.path("roots").size());
        assertEquals(8, multi.path("objects").size());
        int taskCount = 0, resourceCount = 0, jobCount = 0;
        for (JsonNode object : multi.path("objects")) {
            switch (object.path("kind").asText()) {
                case "TASK" -> taskCount++;
                case "RESOURCE" -> resourceCount++;
                case "JOB" -> jobCount++;
                default -> {}
            }
        }
        assertEquals(1, taskCount);
        assertEquals(1, resourceCount);
        assertEquals(2, jobCount);
    }

    @Test
    @Order(2)
    void missingCredentialBlocksWithoutCreatingAnything() throws Exception {
        var plan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                Map.of(),
                                "reviewedEnvironment",
                                true));
        assertEquals("BLOCKED", plan.path("status").asText());
        assertTrue(plan.toString().contains("MISSING_INPUT:/dbConfig/password"));
        assertEquals(0, count(target, "ifj_task"));
        assertEquals(1, count(target, "ifj_resource"));
        assertEquals(
                409,
                send(
                                target,
                                "POST",
                                "/api/job-packages/plans/" + plan.path("id").asText() + "/apply",
                                null)
                        .statusCode());
    }

    @Test
    @Order(3)
    void createsInIndependentDatabaseAndRebindsIds() throws Exception {
        choices =
                Map.of(
                        entry("RESOURCE").path("key").asText(),
                        Map.of(
                                "action",
                                "CREATE",
                                "secrets",
                                Map.of("/dbConfig/password", password)));
        var plan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                choices,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", plan.path("status").asText(), plan.toString());
        assertFalse(plan.toString().contains(password));
        assertEquals("package-admin", plan.path("actor").asText());
        assertFalse(plan.hasNonNull("startedAt"));
        assertFalse(plan.hasNonNull("completedAt"));
        planId = plan.path("id").asText();
        completed = call(target, "POST", "/api/job-packages/plans/" + planId + "/apply", null);
        assertEquals("COMPLETED", completed.path("status").asText(), completed.toString());
        assertEquals("package-admin", completed.path("actor").asText());
        assertFalse(completed.path("startedAt").asText().isBlank());
        assertFalse(completed.path("completedAt").asText().isBlank());
        assertFalse(
                java.time.Instant.parse(completed.path("completedAt").asText())
                        .isBefore(java.time.Instant.parse(completed.path("startedAt").asText())));
        var repeated = call(target, "POST", "/api/job-packages/plans/" + planId + "/apply", null);
        assertEquals(completed.path("startedAt"), repeated.path("startedAt"));
        assertEquals(completed.path("completedAt"), repeated.path("completedAt"));
        var records = call(target, "GET", "/api/job-packages/records", null);
        var history =
                java.util.stream.StreamSupport.stream(records.spliterator(), false)
                        .filter(r -> planId.equals(r.path("id").asText()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(completed.path("actor"), history.path("actor"));
        assertEquals(completed.path("startedAt"), history.path("startedAt"));
        assertEquals(completed.path("completedAt"), history.path("completedAt"));
        String targetResource = step("RESOURCE").path("targetId").asText();
        assertNotEquals(resource, targetResource);
        var taskDetail =
                call(target, "GET", "/api/tasks/" + step("TASK").path("targetId").asText(), null);
        assertEquals(
                targetResource,
                json.readTree(taskDetail.path("pluginConfig").asText())
                        .path("resourceId")
                        .asText());
        assertFalse(taskDetail.path("enabled").asBoolean());
        assertEquals(0, count(target, "ifj_instance"));
        var schedule =
                call(
                        target,
                        "GET",
                        "/api/schedules/" + step("SCHEDULE").path("targetId").asText(),
                        null);
        assertFalse(schedule.path("enabled").asBoolean());
        assertEquals(
                step("CALENDAR").path("targetId").asText(), schedule.path("calendarId").asText());
        assertEquals(
                step("PARAMETER").path("targetId").asText(),
                schedule.path("parameterForm").path("templateId").asText());
        assertEquals("east", schedule.path("variables").path("region").asText());
        var binding =
                call(
                        target,
                        "GET",
                        "/api/parameter-templates/bindings/JOB/"
                                + step("JOB").path("targetId").asText(),
                        null);
        assertEquals(step("PARAMETER").path("targetId").asText(), binding.path("id").asText());
        assertEquals(
                409,
                send(
                                target,
                                "PATCH",
                                "/api/schedules/"
                                        + step("SCHEDULE").path("targetId").asText()
                                        + "/enabled",
                                Map.of("enabled", true))
                        .statusCode());
        assertEquals(
                "VERIFIED",
                call(target, "GET", "/api/job-packages/plans/" + planId + "/verify", null)
                        .path("status")
                        .asText());
        assertEquals(
                "COMPLETED",
                call(target, "POST", "/api/job-packages/plans/" + planId + "/apply", null)
                        .path("status")
                        .asText());
    }

    @Test
    @Order(4)
    void repeatImportUsesDurableMappingsWithoutNewObjects() throws Exception {
        target.context().close();
        target = startNode("target");
        var persisted = call(target, "GET", "/api/job-packages/plans/" + planId, null);
        assertEquals(completed.path("startedAt"), persisted.path("startedAt"));
        assertEquals(completed.path("completedAt"), persisted.path("completedAt"));
        var plan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                Map.of(),
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", plan.path("status").asText(), plan.toString());
        for (JsonNode step : plan.path("steps"))
            assertEquals("UNCHANGED", step.path("status").asText(), plan.toString());
        var resumed =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + plan.path("id").asText() + "/apply",
                        null);
        assertEquals("COMPLETED", resumed.path("status").asText(), resumed.toString());
        assertEquals(1, count(target, "ifj_task"));
        assertEquals(1, count(target, "ifj_job"));
        assertEquals(2, count(target, "ifj_resource"));
    }

    @Test
    @Order(5)
    void stalePreviewCannotOverwriteConcurrentTaskEdit() throws Exception {
        Map<String, Object> updateChoices = new LinkedHashMap<>();
        var body = (ObjectNode) entry("TASK").path("body").deepCopy();
        body.put("description", "package update");
        updateChoices.put(
                entry("TASK").path("key").asText(),
                Map.of(
                        "action",
                        "UPDATE",
                        "targetId",
                        step("TASK").path("targetId").asText(),
                        "body",
                        body));
        var plan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                updateChoices,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", plan.path("status").asText(), plan.toString());
        target.jdbc()
                .update(
                        "UPDATE ifj_task SET name=? WHERE id=?",
                        "Concurrent edit",
                        Long.valueOf(step("TASK").path("targetId").asText()));
        var result =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + plan.path("id").asText() + "/apply",
                        null);
        assertEquals("PARTIAL", result.path("status").asText());
        assertEquals(
                "Concurrent edit",
                call(target, "GET", "/api/tasks/" + step("TASK").path("targetId").asText(), null)
                        .path("name")
                        .asText());
        target.jdbc()
                .update(
                        "UPDATE ifj_task SET name=? WHERE id=?",
                        "Package SQL",
                        Long.valueOf(step("TASK").path("targetId").asText()));
        var resumed =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + plan.path("id").asText() + "/apply",
                        null);
        // Restoring the name does not restore the version/time fingerprint: a new preview is
        // required.
        assertEquals("PARTIAL", resumed.path("status").asText(), resumed.toString());
        var fresh =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                updateChoices,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", fresh.path("status").asText(), fresh.toString());
        var applied =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + fresh.path("id").asText() + "/apply",
                        null);
        assertEquals("COMPLETED", applied.path("status").asText(), applied.toString());
        assertEquals(1, count(target, "ifj_task"));
        assertEquals(2, count(target, "ifj_resource"));
        assertEquals(
                "DRIFTED",
                call(target, "GET", "/api/job-packages/plans/" + planId + "/verify", null)
                        .path("status")
                        .asText());
    }

    @Test
    @Order(6)
    void referenceOnlyExportRequiresMappingAndPreservesTargetResource() throws Exception {
        String file = referenceContent;
        var bundle = call(target, "POST", "/api/job-packages/inspect", Map.of("content", file));
        JsonNode ref = null;
        for (JsonNode e : bundle.path("objects"))
            if (e.path("kind").asText().equals("RESOURCE")) ref = e;
        assertTrue(ref.path("referenceOnly").asBoolean());
        assertFalse(ref.path("body").has("dbConfig"));
        assertEquals(2, count(target, "ifj_resource"));
    }

    @Test
    @Order(7)
    void viewersCannotExportOrApply() throws Exception {
        call(
                target,
                "POST",
                "/api/users",
                Map.of("username", "package-viewer", "password", password, "roleCode", "VIEWER"));
        target.jdbc().update("UPDATE ifj_auth_user SET must_change_password=0");
        String token =
                call(
                                new Node(target.context(), target.base(), null, target.jdbc()),
                                "POST",
                                "/api/auth/login",
                                Map.of("username", "package-viewer", "password", password))
                        .path("tokenValue")
                        .asText();
        Node viewer = new Node(target.context(), target.base(), token, target.jdbc());
        assertEquals(
                403,
                send(
                                viewer,
                                "POST",
                                "/api/job-packages/export",
                                Map.of("roots", List.of(Map.of("kind", "TASK", "id", task))))
                        .statusCode());
        assertEquals(
                403,
                send(viewer, "POST", "/api/job-packages/plans/" + planId + "/apply", null)
                        .statusCode());
    }

    @Test
    @Order(8)
    void exportDoesNotSilentlyChangePinnedJobDependencies() throws Exception {
        assertEquals(
                409,
                send(
                                target,
                                "POST",
                                "/api/job-packages/export",
                                Map.of(
                                        "roots",
                                        List.of(
                                                Map.of(
                                                        "kind",
                                                        "JOB",
                                                        "id",
                                                        step("JOB").path("targetId").asText()))))
                        .statusCode());
    }

    @Test
    @Order(9)
    void simultaneousMultiRootApplyCreatesSharedObjectsOnce() throws Exception {
        var reuse =
                Map.of(
                        entry("TASK").path("key").asText(),
                        Map.of(
                                "action",
                                "REUSE",
                                "targetId",
                                step("TASK").path("targetId").asText()));
        var plan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                multiContent,
                                "choices",
                                reuse,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", plan.path("status").asText(), plan.toString());
        String path = "/api/job-packages/plans/" + plan.path("id").asText() + "/apply";
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> call(target, "POST", path, null));
            var second = executor.submit(() -> call(target, "POST", path, null));
            assertEquals(
                    "COMPLETED",
                    first.get(30, java.util.concurrent.TimeUnit.SECONDS).path("status").asText());
            assertEquals(
                    "COMPLETED",
                    second.get(30, java.util.concurrent.TimeUnit.SECONDS).path("status").asText());
        }
        assertEquals(1, count(target, "ifj_task"));
        assertEquals(2, count(target, "ifj_job"));
        assertEquals(2, count(target, "ifj_resource"));
    }

    @Test
    @Order(12)
    void cliUsesRealAuthenticatedApiAndProducesCredentialFreeFiles() throws Exception {
        Path connection = directory.resolve("cli-connection.json");
        json.writeValue(
                connection.toFile(),
                Map.of("url", target.base(), "username", "package-admin", "password", password));
        Path archive = directory.resolve("cli-export.ijob.zip"),
                report = directory.resolve("cli-inspect.json");
        assertEquals(
                0,
                cli(
                        connection,
                        "export",
                        "--kind",
                        "TASK",
                        "--ids",
                        step("TASK").path("targetId").asText(),
                        "--resources",
                        "--out",
                        archive.toString()));
        assertEquals(
                0,
                cli(
                        connection,
                        "inspect",
                        "--bundle",
                        archive.toString(),
                        "--out",
                        report.toString()));
        String text = java.nio.file.Files.readString(report);
        assertFalse(text.contains(password));
        assertEquals(2, json.readTree(text).path("objects").size());
        assertEquals(
                3,
                cli(
                        connection,
                        "verify",
                        "--plan",
                        planId,
                        "--out",
                        directory.resolve("cli-verify.json").toString()));
    }

    private int cli(Path connection, String... arguments) throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null
                && !java.nio.file.Files.isRegularFile(
                        root.resolve("tools/job-package/ijob_package.py"))) root = root.getParent();
        assertTrue(root != null, "CLI source root must be available");
        var command = new java.util.ArrayList<String>();
        command.add(System.getenv().getOrDefault("IJOB_TEST_PYTHON", "python"));
        command.add(root.resolve("tools/job-package/ijob_package.py").toString());
        command.add("--connection");
        command.add(connection.toString());
        command.addAll(List.of(arguments));
        Path log = directory.resolve("cli-" + UUID.randomUUID() + ".log");
        Process process =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        try {
            assertTrue(
                    process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
                    "CLI process timeout");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    @Order(10)
    void concurrencyResourcesDeduplicateRebindAndKeepGlobalPoliciesUntouched() throws Exception {
        target.context().close();
        source = startNode("source");
        String shared =
                call(
                                source,
                                "POST",
                                "/api/concurrency/resources",
                                Map.of(
                                        "code",
                                        "package-shared",
                                        "name",
                                        "Package shared capacity",
                                        "maxConcurrency",
                                        7,
                                        "enabled",
                                        true))
                        .asText();
        String first =
                call(source, "POST", "/api/tasks", task("Policy first", resource, "SELECT 1"))
                        .path("taskId")
                        .asText();
        String second =
                call(source, "POST", "/api/tasks", task("Policy second", resource, "SELECT 2"))
                        .path("taskId")
                        .asText();
        call(
                source,
                "PUT",
                "/api/concurrency/tasks/" + first,
                Map.of(
                        "expectedVersion",
                        0,
                        "taskMaxConcurrency",
                        3,
                        "resourceIds",
                        List.of(shared)));
        call(
                source,
                "PUT",
                "/api/concurrency/tasks/" + second,
                Map.of("expectedVersion", 0, "resourceIds", List.of(shared)));
        var roots =
                List.of(Map.of("kind", "TASK", "id", first), Map.of("kind", "TASK", "id", second));
        String payload =
                call(
                                source,
                                "POST",
                                "/api/job-packages/export",
                                Map.of("roots", roots, "includeResources", true))
                        .path("content")
                        .asText();
        String references =
                call(
                                source,
                                "POST",
                                "/api/job-packages/export",
                                Map.of("roots", roots, "includeResources", false))
                        .path("content")
                        .asText();
        source.context().close();
        source = null;
        target = startNode("target");
        call(
                target,
                "POST",
                "/api/concurrency/resources",
                Map.of(
                        "code",
                        "target-reserved",
                        "name",
                        "Target reserved capacity",
                        "maxConcurrency",
                        2,
                        "enabled",
                        true));
        JsonNode globals = call(target, "GET", "/api/concurrency/plugin-policies", null);
        JsonNode contents =
                call(target, "POST", "/api/job-packages/inspect", Map.of("content", payload));
        assertEquals(4, contents.path("objects").size());
        JsonNode capacity = null;
        Map<String, Object> selected = new LinkedHashMap<>();
        for (JsonNode entry : contents.path("objects")) {
            if (entry.path("kind").asText().equals("RESOURCE"))
                selected.put(
                        entry.path("key").asText(),
                        Map.of(
                                "action",
                                "REUSE",
                                "targetId",
                                step("RESOURCE").path("targetId").asText()));
            if (entry.path("kind").asText().equals("CONCURRENCY_RESOURCE")) capacity = entry;
            if (entry.path("kind").asText().equals("TASK")) {
                assertTrue(
                        entry.path("body")
                                .path("concurrencyPolicy")
                                .path("resourceIds")
                                .get(0)
                                .isNull());
                assertFalse(entry.path("body").path("concurrencyPolicy").has("version"));
            }
        }
        assertTrue(capacity != null);
        assertEquals(7, capacity.path("body").path("maxConcurrency").asInt());
        assertFalse(capacity.toString().contains("activeInstanceCount"));
        assertFalse(capacity.toString().contains("referenceCount"));
        var preview =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                payload,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", preview.path("status").asText(), preview.toString());
        var done =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + preview.path("id").asText() + "/apply",
                        null);
        assertEquals("COMPLETED", done.path("status").asText(), done.toString());
        String capacityId = null, targetTask = null, taskKey = null;
        for (JsonNode entry : done.path("steps")) {
            selected.put(
                    entry.path("key").asText(),
                    Map.of("action", "REUSE", "targetId", entry.path("targetId").asText()));
            if (entry.path("kind").asText().equals("CONCURRENCY_RESOURCE"))
                capacityId = entry.path("targetId").asText();
            if (entry.path("name").asText().equals("Policy first")) {
                targetTask = entry.path("targetId").asText();
                taskKey = entry.path("key").asText();
            }
        }
        assertNotEquals(shared, capacityId);
        JsonNode policy = call(target, "GET", "/api/concurrency/tasks/" + targetTask, null);
        assertEquals(3, policy.path("taskMaxConcurrency").asInt());
        assertEquals(capacityId, policy.path("resources").get(0).path("id").asText());
        assertFalse(
                call(target, "GET", "/api/tasks/" + targetTask, null).path("enabled").asBoolean());
        assertEquals(globals, call(target, "GET", "/api/concurrency/plugin-policies", null));
        assertEquals(
                "VERIFIED",
                call(
                                target,
                                "GET",
                                "/api/job-packages/plans/" + done.path("id").asText() + "/verify",
                                null)
                        .path("status")
                        .asText());
        assertEquals(0, count(target, "ifj_instance"));

        // Explicit task reuse must retain the destination policy even when source capacity differs.
        call(
                target,
                "PUT",
                "/api/concurrency/tasks/" + targetTask,
                Map.of(
                        "expectedVersion",
                        policy.path("version").asText(),
                        "taskMaxConcurrency",
                        5,
                        "resourceIds",
                        List.of(capacityId)));
        var reused =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                payload,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", reused.path("status").asText(), reused.toString());
        assertEquals(
                "COMPLETED",
                call(
                                target,
                                "POST",
                                "/api/job-packages/plans/" + reused.path("id").asText() + "/apply",
                                null)
                        .path("status")
                        .asText());
        assertEquals(
                5,
                call(target, "GET", "/api/concurrency/tasks/" + targetTask, null)
                        .path("taskMaxConcurrency")
                        .asInt());
        selected.put(taskKey, Map.of("action", "UPDATE", "targetId", targetTask));
        var update =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                payload,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", update.path("status").asText(), update.toString());
        var updated =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + update.path("id").asText() + "/apply",
                        null);
        assertEquals("COMPLETED", updated.path("status").asText(), updated.toString());
        assertEquals(
                3,
                call(target, "GET", "/api/concurrency/tasks/" + targetTask, null)
                        .path("taskMaxConcurrency")
                        .asInt());

        // A policy edit after preview invalidates task publication.
        var stale =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                payload,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        policy = call(target, "GET", "/api/concurrency/tasks/" + targetTask, null);
        call(
                target,
                "PUT",
                "/api/concurrency/tasks/" + targetTask,
                Map.of(
                        "expectedVersion",
                        policy.path("version").asText(),
                        "taskMaxConcurrency",
                        6,
                        "resourceIds",
                        List.of(capacityId)));
        var failed =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + stale.path("id").asText() + "/apply",
                        null);
        assertEquals("PARTIAL", failed.path("status").asText());
        assertTrue(failed.toString().contains("TARGET_CHANGED"));
        assertEquals(
                6,
                call(target, "GET", "/api/concurrency/tasks/" + targetTask, null)
                        .path("taskMaxConcurrency")
                        .asInt());

        JsonNode refContents =
                call(target, "POST", "/api/job-packages/inspect", Map.of("content", references));
        for (JsonNode entry : refContents.path("objects"))
            if (entry.path("kind").asText().equals("CONCURRENCY_RESOURCE")) {
                assertTrue(entry.path("referenceOnly").asBoolean());
                assertFalse(entry.path("body").has("maxConcurrency"));
            }
        Map<String, Object> missing = new LinkedHashMap<>(selected);
        missing.put(capacity.path("key").asText(), Map.of("action", "CREATE"));
        var blocked =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                references,
                                "choices",
                                missing,
                                "reviewedEnvironment",
                                true));
        assertEquals("BLOCKED", blocked.path("status").asText());
        assertTrue(blocked.toString().contains("MAP_REQUIRED"));
        var mapped =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                references,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", mapped.path("status").asText(), mapped.toString());
        assertEquals(
                "COMPLETED",
                call(
                                target,
                                "POST",
                                "/api/job-packages/plans/" + mapped.path("id").asText() + "/apply",
                                null)
                        .path("status")
                        .asText());
        Map<String, Object> invalid = new LinkedHashMap<>(selected);
        ObjectNode invalidBody =
                ((ObjectNode) capacity.path("body"))
                        .deepCopy()
                        .put("maxConcurrency", 0)
                        .put("name", "Different name same code");
        invalid.put(capacity.path("key").asText(), Map.of("action", "CREATE", "body", invalidBody));
        var invalidPlan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                payload,
                                "choices",
                                invalid,
                                "reviewedEnvironment",
                                true));
        assertEquals("BLOCKED", invalidPlan.path("status").asText());
        assertTrue(invalidPlan.toString().contains("INVALID_CONFIGURATION"));
        assertTrue(invalidPlan.toString().contains("NAME_CONFLICT"));
        var resourceState = call(target, "GET", "/api/concurrency/resources/" + capacityId, null);
        Map<String, Object> disabled = new LinkedHashMap<>();
        disabled.put("expectedVersion", resourceState.path("version").asText());
        disabled.put("code", "package-shared");
        disabled.put("name", "Package shared capacity");
        disabled.put("maxConcurrency", 7);
        disabled.put("enabled", false);
        call(target, "PUT", "/api/concurrency/resources/" + capacityId, disabled);
        var disabledPlan =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                references,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("BLOCKED", disabledPlan.path("status").asText());
        assertTrue(disabledPlan.toString().contains("INCOMPATIBLE_TARGET"));
        assertEquals(2, count(target, "ifj_concurrency_resource"));
    }

    @Test
    @Order(11)
    void legacyPackageUpdatePreservesDestinationConcurrencyPolicy() throws Exception {
        String taskId = step("TASK").path("targetId").asText();
        var before = call(target, "GET", "/api/concurrency/tasks/" + taskId, null);
        var policy =
                call(
                        target,
                        "PUT",
                        "/api/concurrency/tasks/" + taskId,
                        Map.of(
                                "expectedVersion",
                                before.path("version").asText(),
                                "taskMaxConcurrency",
                                8,
                                "resourceIds",
                                List.of()));
        Map<String, Object> selected = new LinkedHashMap<>();
        for (JsonNode entry : completed.path("steps"))
            selected.put(
                    entry.path("key").asText(),
                    Map.of(
                            "action",
                            entry.path("kind").asText().equals("TASK") ? "UPDATE" : "REUSE",
                            "targetId",
                            entry.path("targetId").asText()));
        var preview =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans",
                        Map.of(
                                "content",
                                content,
                                "choices",
                                selected,
                                "reviewedEnvironment",
                                true));
        assertEquals("READY", preview.path("status").asText(), preview.toString());
        var done =
                call(
                        target,
                        "POST",
                        "/api/job-packages/plans/" + preview.path("id").asText() + "/apply",
                        null);
        assertEquals("COMPLETED", done.path("status").asText(), done.toString());
        var after = call(target, "GET", "/api/concurrency/tasks/" + taskId, null);
        assertEquals(policy.path("version"), after.path("version"));
        assertEquals(8, after.path("taskMaxConcurrency").asInt());
        for (JsonNode step : preview.path("steps"))
            if (step.path("action").asText().equals("REUSE"))
                assertEquals(step.path("before"), step.path("after"), step.toString());
        for (JsonNode step : done.path("steps"))
            if (step.path("action").asText().equals("REUSE"))
                assertEquals(step.path("before"), step.path("after"), step.toString());
    }

    private JsonNode entry(String kind) {
        for (JsonNode e : inspected.path("objects"))
            if (e.path("kind").asText().equals(kind)) return e;
        throw new AssertionError(kind);
    }

    private JsonNode step(String kind) {
        for (JsonNode e : completed.path("steps"))
            if (e.path("kind").asText().equals(kind)) return e;
        throw new AssertionError(kind);
    }

    private int count(Node node, String table) {
        return node.jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private JsonNode call(Node node, String method, String path, Object body) throws Exception {
        var response = send(node, method, path, body);
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, response.body());
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> send(Node node, String method, String path, Object body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(node.base() + path))
                        .header("Content-Type", "application/json");
        if (node.token() != null) request.header("ijob-token", node.token());
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
