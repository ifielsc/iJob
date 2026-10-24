// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.Map;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FilesHttpIT {
    @TempDir static Path directory;
    private ServletWebServerApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private JdbcTemplate jdbc;
    private String base;
    private String adminToken;
    private String viewerToken;
    private String operatorToken;
    private String resourceId;
    private String taskId;
    private static final String INITIAL = "Http-initial-password-123";

    @BeforeAll
    void start() throws Exception {
        String url =
                "jdbc:sqlite:"
                        + directory.resolve("files-http.db").toAbsolutePath()
                        + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE";
        context =
                (ServletWebServerApplicationContext)
                        new SpringApplicationBuilder(IJobApplication.class)
                                .registerShutdownHook(false)
                                .run(
                                        "--server.address=127.0.0.1",
                                        "--server.port=0",
                                        "--spring.config.import=optional:classpath:database/sqlite.yml",
                                        "--spring.datasource.url=" + url,
                                        "--spring.datasource.hikari.minimum-idle=0",
                                        "--spring.flyway.enabled=true",
                                        "--spring.flyway.locations=classpath:db/release/sqlite",
                                        "--app.platform-database.vendor=SQLITE",
                                        "--app.runtime.mode=STANDALONE",
                                        "--app.runtime.instance-name=files-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=files-http-fixture",
                                        "--app.security.aes.key="
                                                + java.util.Base64.getEncoder()
                                                        .encodeToString(new byte[32]),
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=legacy-admin",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4).encode(INITIAL),
                                        "--app.schedule.enabled=false",
                                        "--app.event.recovery.enabled=false",
                                        "--app.event.ingress.enabled=false",
                                        "--app.notification.outbox.enabled=false",
                                        "--app.runtime.concurrency-permit.wait-timeout=17s",
                                        "--app.retention.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--logging.level.root=OFF");
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        adminToken = login("legacy-admin", INITIAL);
        for (String role : new String[] {"VIEWER", "OPERATOR"}) {
            assertEquals(
                    200,
                    send(
                                    "POST",
                                    "/api/users",
                                    Map.of(
                                            "username",
                                            role.toLowerCase(),
                                            "password",
                                            INITIAL,
                                            "roleCode",
                                            role),
                                    adminToken)
                            .statusCode());
        }
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        viewerToken = login("viewer", INITIAL);
        operatorToken = login("operator", INITIAL);
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
    }

    private Map<String, Object> task(String type, String config) {
        return Map.of(
                "name",
                "File fixture",
                "pluginType",
                type,
                "pluginConfig",
                config,
                "enabled",
                true,
                "groups",
                java.util.List.of(),
                "retryTimes",
                0,
                "retryInterval",
                0);
    }

    private Map<String, Object> local(String code, String path) {
        return Map.of(
                "name",
                code,
                "code",
                code,
                "type",
                "FILE",
                "enabled",
                true,
                "fileConfig",
                Map.of("protocol", "LOCAL", "basePath", path));
    }

    private String transfer(String source, String target) {
        return "{\"sourceResourceId\":"
                + source
                + ",\"targetResourceId\":"
                + target
                + ",\"sourcePath\":\"/\",\"targetPath\":\"/\",\"filePattern\":\"*.csv\"}";
    }

    @Test
    @Order(1)
    void resourceRootAndTaskPublicationAreEditableWithoutRunning() throws Exception {
        var root = java.nio.file.Files.createDirectory(directory.resolve("source"));
        java.nio.file.Files.writeString(root.resolve("report.csv"), "fixture");
        var response =
                send("POST", "/api/resources", local("file-source", root.toString()), adminToken);
        assertEquals(201, response.statusCode(), response.body());
        resourceId = data(response).asText();
        var target = java.nio.file.Files.createDirectory(directory.resolve("target"));
        response =
                send("POST", "/api/resources", local("file-target", target.toString()), adminToken);
        assertEquals(201, response.statusCode(), response.body());
        String targetId = data(response).asText();
        response = send("GET", "/api/resources/" + resourceId, null, adminToken);
        assertEquals(root.toString(), data(response).path("config").path("basePath").asText());
        assertEquals(
                200,
                send("POST", "/api/resources/" + resourceId + "/connection-test", null, adminToken)
                        .statusCode());
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/resources/" + resourceId,
                                Map.of(
                                        "name",
                                        "Renamed",
                                        "code",
                                        "file-source",
                                        "type",
                                        "FILE",
                                        "enabled",
                                        true,
                                        "fileConfig",
                                        Map.of("preserveExisting", true)),
                                adminToken)
                        .statusCode());
        response =
                send(
                        "POST",
                        "/api/tasks",
                        task("FILE_TRANSFER", transfer(resourceId, targetId)),
                        adminToken);
        assertEquals(201, response.statusCode(), response.body());
        taskId = data(response).path("taskId").asText();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_instance", Integer.class));
        assertTrue(java.nio.file.Files.exists(root.resolve("report.csv")));
        try (var files = java.nio.file.Files.list(target)) {
            assertEquals(0, files.count());
        }
        response =
                send(
                        "POST",
                        "/api/tasks",
                        task(
                                "EVENT",
                                "{\"action\":\"PROBE_FILE\",\"fileResourceId\":"
                                        + resourceId
                                        + ",\"filePath\":\"/\",\"eventCode\":\"fixture.ready\"}"),
                        adminToken);
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_instance", Integer.class));
    }

    @Test
    @Order(2)
    void invalidIdsTypesStatesAndPermissionsFailBeforePublication() throws Exception {
        String config = transfer(resourceId, resourceId);
        for (String bad : new String[] {"1.5", "1e0", "9223372036854775808", "0", "-1"}) {
            assertEquals(
                    400,
                    send(
                                    "POST",
                                    "/api/tasks",
                                    task(
                                            "FILE_TRANSFER",
                                            config.replace(
                                                    "\"sourceResourceId\":" + resourceId,
                                                    "\"sourceResourceId\":" + bad)),
                                    adminToken)
                            .statusCode());
            assertEquals(
                    400,
                    send(
                                    "POST",
                                    "/api/tasks",
                                    task(
                                            "EVENT",
                                            "{\"action\":\"PROBE_FILE\",\"fileResourceId\":"
                                                    + bad
                                                    + ",\"filePath\":\"/\",\"eventCode\":\"fixture.ready\"}"),
                                    adminToken)
                            .statusCode());
        }
        jdbc.update("UPDATE ifj_resource SET enabled=0 WHERE id=?", Long.parseLong(resourceId));
        assertEquals(
                400,
                send("POST", "/api/tasks", task("FILE_TRANSFER", config), adminToken).statusCode());
        jdbc.update(
                "UPDATE ifj_resource SET enabled=1,type='DB' WHERE id=?",
                Long.parseLong(resourceId));
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                task(
                                        "EVENT",
                                        "{\"action\":\"PROBE_FILE\",\"fileResourceId\":"
                                                + resourceId
                                                + ",\"filePath\":\"/\",\"eventCode\":\"fixture.ready\"}"),
                                adminToken)
                        .statusCode());
        jdbc.update("UPDATE ifj_resource SET type='FILE' WHERE id=?", Long.parseLong(resourceId));
        assertEquals(
                403,
                send("POST", "/api/tasks", task("FILE_TRANSFER", config), viewerToken)
                        .statusCode());
        assertEquals(
                403,
                send(
                                "POST",
                                "/api/resources/" + resourceId + "/connection-test",
                                null,
                                operatorToken)
                        .statusCode());
        assertEquals(
                409, send("DELETE", "/api/resources/" + resourceId, null, adminToken).statusCode());
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                task(
                                        "FILE_TRANSFER",
                                        config.replace("{", "{\"deleteSource\":true,")),
                                adminToken)
                        .statusCode());
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                task(
                                        "EVENT",
                                        "{\"action\":\"PROBE_FILE\",\"fileResourceId\":"
                                                + resourceId
                                                + ",\"filePath\":\"\",\"eventCode\":\"fixture.ready\"}"),
                                adminToken)
                        .statusCode());
    }

    @Test
    @Order(3)
    void immutableHistoryProtectsFileReferencesAndRevisionConflicts() throws Exception {
        String readOnly =
                "{\"mode\":\"READ_ONLY\",\"sourceResourceId\":"
                        + resourceId
                        + ",\"sourcePath\":\"/\"}";
        var response =
                send(
                        "PUT",
                        "/api/tasks/" + taskId + "?expectedRevision=1",
                        task("FILE_TRANSFER", readOnly),
                        adminToken);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(
                409,
                send(
                                "PUT",
                                "/api/tasks/" + taskId + "?expectedRevision=1",
                                task("FILE_TRANSFER", readOnly),
                                adminToken)
                        .statusCode());
        String target =
                jdbc.queryForObject(
                        "SELECT CAST(id AS TEXT) FROM ifj_resource WHERE code='file-target'",
                        String.class);
        assertEquals(
                409, send("DELETE", "/api/resources/" + target, null, adminToken).statusCode());
    }

    @Test
    @Order(4)
    void publicationAndResourceDeletionCannotBothCommit() throws Exception {
        var response =
                send(
                        "POST",
                        "/api/resources",
                        local("race-root", directory.toString()),
                        adminToken);
        assertEquals(201, response.statusCode());
        String id = data(response).asText();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var publication =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                                "POST",
                                                "/api/tasks",
                                                task(
                                                        "FILE_TRANSFER",
                                                        "{\"mode\":\"READ_ONLY\",\"sourceResourceId\":"
                                                                + id
                                                                + "}"),
                                                adminToken)
                                        .statusCode();
                            });
            var deletion =
                    executor.submit(
                            () -> {
                                start.await();
                                return send("DELETE", "/api/resources/" + id, null, adminToken)
                                        .statusCode();
                            });
            start.countDown();
            int p = publication.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    d = deletion.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(
                    (p == 201 && d == 409) || (p == 404 && d == 200),
                    "Publish/delete: " + p + "/" + d);
        }
    }

    @Test
    @Order(5)
    void browserAndPreviewAreAdminOnlyAndNeverCreateTaskRuns() throws Exception {
        var opening = Map.of("resourceId", resourceId);
        for (String token : new String[] {viewerToken, operatorToken}) {
            assertEquals(
                    403, send("POST", "/api/file-browser/sessions", opening, token).statusCode());
            assertEquals(403, send("GET", "/api/file-browser/roots", null, token).statusCode());
            assertEquals(403, send("POST", "/api/sql-preview", Map.of(), token).statusCode());
            assertEquals(
                    403,
                    send(
                                    "POST",
                                    "/api/sql-preview/" + java.util.UUID.randomUUID() + "/cancel",
                                    null,
                                    token)
                            .statusCode());
        }
        var response = send("POST", "/api/file-browser/sessions", opening, adminToken);
        assertEquals(200, response.statusCode(), response.body());
        String session = data(response).path("id").asText();
        response = send("GET", "/api/file-browser/sessions/" + session, null, adminToken);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(data(response).path("entries").isArray());
        assertEquals(
                400,
                send(
                                "GET",
                                "/api/file-browser/sessions/" + session + "?path=..%2F",
                                null,
                                adminToken)
                        .statusCode());
        assertEquals(
                200,
                send("DELETE", "/api/file-browser/sessions/" + session, null, adminToken)
                        .statusCode());
        assertEquals(
                409,
                send("GET", "/api/file-browser/sessions/" + session, null, adminToken)
                        .statusCode());
        response =
                send(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                "Preview DB",
                                "code",
                                "preview-db",
                                "type",
                                "DB",
                                "enabled",
                                true,
                                "dbConfig",
                                Map.of(
                                        "dbType",
                                        "MYSQL",
                                        "jdbcUrl",
                                        "jdbc:mysql://127.0.0.1:1/never-connect",
                                        "username",
                                        "fixture")),
                        adminToken);
        assertEquals(201, response.statusCode(), response.body());
        String dbId = data(response).asText();
        response =
                send(
                        "POST",
                        "/api/sql-preview",
                        Map.of(
                                "requestId",
                                java.util.UUID.randomUUID().toString(),
                                "resourceId",
                                dbId,
                                "mode",
                                "SQL",
                                "sql",
                                "SELECT 1; DELETE FROM blocked",
                                "maxRows",
                                200),
                        adminToken);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("READ_ONLY", data(response).path("status").asText(), response.body());
        assertEquals(0, data(response).path("rows").size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_instance", Integer.class));
    }

    private String login(String username, String password) throws Exception {
        var response =
                send(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", username, "password", password),
                        null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("ijob-token", data(response).path("tokenName").asText());
        return data(response).path("tokenValue").asText();
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> send(String method, String path, Object body, String token)
            throws Exception {
        var builder =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json");
        if (token != null) builder.header("ijob-token", token);
        builder.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
