package com.ifelio.ijob;

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
class DataCheckHttpIT {
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
                        + directory.resolve("datacheck-http.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=datacheck-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=datacheck-http-fixture",
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

    private String config(boolean handler) {
        return "{\"resourceId\":"
                + resourceId
                + ",\"checks\":[{\"name\":\"amount\",\"type\":\"NORMAL\",\"sql\":\"select"
                + " 1\",\"expected\":0.123456789012345678901}],\"handlers\":"
                + (handler
                        ? "[{\"type\":\"NOTIFY\",\"params\":{\"notifyConfigId\":9007199254740993}}]"
                        : "[]")
                + "}";
    }

    private Map<String, Object> task(String config) {
        return Map.of(
                "name",
                "Data check fixture",
                "pluginType",
                "DATA_CHECK",
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

    @Test
    @Order(1)
    void publicationPreservesPrecisionAndDoesNotExecute() throws Exception {
        var response =
                send(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                "Check DB",
                                "code",
                                "check-db",
                                "type",
                                "DB",
                                "enabled",
                                true,
                                "dbConfig",
                                Map.of(
                                        "dbType",
                                        "POSTGRESQL",
                                        "jdbcUrl",
                                        "jdbc:postgresql://127.0.0.1:1/never-connect",
                                        "username",
                                        "fixture",
                                        "password",
                                        INITIAL)),
                        adminToken);
        assertEquals(201, response.statusCode(), response.body());
        resourceId = data(response).asText();
        jdbc.update(
                "INSERT INTO ifj_notify_config(id,name,type,config,enabled)"
                        + " VALUES(9007199254740993,'Do not dispatch','DINGTALK','{}',1)");
        response = send("POST", "/api/tasks", task(config(true)), adminToken);
        assertEquals(201, response.statusCode(), response.body());
        taskId = data(response).path("taskId").asText();
        var detail = data(send("GET", "/api/tasks/" + taskId, null, adminToken));
        assertTrue(detail.path("pluginConfig").asText().contains("0.123456789012345678901"));
        assertTrue(detail.path("pluginConfig").asText().contains("9007199254740993"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_trigger", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_instance", Integer.class));
    }

    @Test
    @Order(2)
    void historicalNotificationReferenceRemainsProtectedAndRevisionIsRequired() throws Exception {
        assertEquals(
                409,
                send("DELETE", "/api/notify-configs/9007199254740993", null, adminToken)
                        .statusCode());
        var response =
                send(
                        "PUT",
                        "/api/tasks/" + taskId + "?expectedRevision=1",
                        task(config(false)),
                        adminToken);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(
                409,
                send("DELETE", "/api/notify-configs/9007199254740993", null, adminToken)
                        .statusCode());
        assertEquals(
                409,
                send(
                                "PUT",
                                "/api/tasks/" + taskId + "?expectedRevision=1",
                                task(config(false)),
                                adminToken)
                        .statusCode());
        assertEquals(
                403, send("POST", "/api/tasks", task(config(false)), viewerToken).statusCode());
        assertEquals(403, send("GET", "/api/tasks/" + taskId, null, viewerToken).statusCode());
    }

    @Test
    @Order(3)
    void invalidReferencesAndOutputCollisionsAreRejected() throws Exception {
        jdbc.update("UPDATE ifj_resource SET enabled=0 WHERE id=?", Long.parseLong(resourceId));
        assertEquals(400, send("POST", "/api/tasks", task(config(false)), adminToken).statusCode());
        jdbc.update(
                "UPDATE ifj_resource SET enabled=1,type='SSH' WHERE id=?",
                Long.parseLong(resourceId));
        assertEquals(400, send("POST", "/api/tasks", task(config(false)), adminToken).statusCode());
        jdbc.update("UPDATE ifj_resource SET type='DB' WHERE id=?", Long.parseLong(resourceId));
        jdbc.update("UPDATE ifj_notify_config SET enabled=0 WHERE id=9007199254740993");
        assertEquals(400, send("POST", "/api/tasks", task(config(true)), adminToken).statusCode());
        jdbc.update("UPDATE ifj_notify_config SET enabled=1 WHERE id=9007199254740993");
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                task(
                                        config(true)
                                                .replace("9007199254740993", "9007199254740993.1")),
                                adminToken)
                        .statusCode());
        String collision =
                config(false)
                        .replace("\"handlers\":[]", "\"handlers\":[{\"type\":\"VARIABLE\"}]")
                        .replace("\"name\":\"amount\"", "\"name\":\"data_check_result\"");
        assertEquals(400, send("POST", "/api/tasks", task(collision), adminToken).statusCode());
        assertEquals(
                2, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_task_definition", Integer.class));
    }

    @Test
    @Order(4)
    void publicationAndNotificationDeletionCannotBothCommit() throws Exception {
        jdbc.update(
                "INSERT INTO ifj_notify_config(id,name,type,config,enabled)"
                        + " VALUES(9007199254740994,'Race fixture','DINGTALK','{}',1)");
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
                                                config(true)
                                                        .replace(
                                                                "9007199254740993",
                                                                "9007199254740994")),
                                        adminToken);
                            });
            var deletion =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                        "DELETE",
                                        "/api/notify-configs/9007199254740994",
                                        null,
                                        adminToken);
                            });
            start.countDown();
            int published = publication.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode();
            int deleted = deletion.get(15, java.util.concurrent.TimeUnit.SECONDS).statusCode();
            assertTrue(
                    (published == 201 && deleted == 409) || (published == 400 && deleted == 200),
                    "Unexpected publish/delete statuses: " + published + "/" + deleted);
        }
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
