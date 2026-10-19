package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.resource.ResourceService;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PushHttpIT {
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
                        + directory.resolve("push-http.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=push-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=push-http-fixture",
                                        "--app.security.aes.key="
                                                + Base64.getEncoder().encodeToString(new byte[32]),
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
                "Push fixture",
                "pluginType",
                type,
                "pluginConfig",
                config,
                "enabled",
                true,
                "groups",
                List.of(),
                "retryTimes",
                0,
                "retryInterval",
                0);
    }

    private String sourceId;
    private String rabbitId;

    private String createResource(String type, String code, Map<String, Object> config)
            throws Exception {
        String field =
                type.equals("DB")
                        ? "dbConfig"
                        : type.equals("KAFKA") ? "kafkaConfig" : "rabbitConfig";
        var response =
                send(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name", code, "code", code, "type", type, "enabled", true, field,
                                config),
                        adminToken);
        assertEquals(201, response.statusCode(), response.body());
        return data(response).asText();
    }

    private String config(String src, String target, String type) {
        return "{\"sourceResourceId\":"
                + src
                + ",\"sql\":\"SELECT id FROM orders\",\"target\":{\"type\":\""
                + type
                + "\",\"resourceId\":"
                + target
                + ",\"properties\":"
                + (type.equals("KAFKA")
                        ? "{\"topic\":\"orders\"}"
                        : "{\"exchange\":\"\",\"routingKey\":\"orders\"}")
                + "}}";
    }

    @Test
    @Order(1)
    void publishesBothTargetsWithoutConnectingAndPreservesSecrets() throws Exception {
        sourceId =
                createResource(
                        "DB",
                        "source",
                        Map.of(
                                "dbType",
                                "MYSQL",
                                "jdbcUrl",
                                "jdbc:mysql://127.0.0.1:1/orders",
                                "username",
                                "reader",
                                "password",
                                "fixture-db"));
        resourceId =
                createResource(
                        "KAFKA", "kafka", Map.of("bootstrapServers", "127.0.0.1:1", "acks", "all"));
        rabbitId =
                createResource(
                        "RABBITMQ",
                        "rabbit",
                        Map.of(
                                "host",
                                "127.0.0.1",
                                "port",
                                1,
                                "username",
                                "publisher",
                                "password",
                                "fixture-mq",
                                "exchange",
                                "legacy"));
        for (String type : new String[] {"KAFKA", "RABBITMQ"}) {
            var response =
                    send(
                            "POST",
                            "/api/tasks",
                            task(
                                    "PUSH",
                                    config(
                                            sourceId,
                                            type.equals("KAFKA") ? resourceId : rabbitId,
                                            type)),
                            adminToken);
            assertEquals(201, response.statusCode(), response.body());
            taskId = data(response).path("taskId").asText();
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_instance", Integer.class));
        var response = send("GET", "/api/resources/" + rabbitId, null, adminToken);
        assertTrue(!response.body().contains("fixture-mq"));
        assertEquals("publisher", data(response).path("config").path("username").asText());
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/resources/" + rabbitId,
                                Map.of(
                                        "name",
                                        "Renamed",
                                        "code",
                                        "rabbit",
                                        "type",
                                        "RABBITMQ",
                                        "rabbitConfig",
                                        Map.of("preserveExisting", true)),
                                adminToken)
                        .statusCode());
        var service = context.getBean(ResourceService.class);
        var cipher = context.getBean(ResourceConfigCipher.class);
        assertEquals(
                "fixture-mq",
                json.readTree(
                                cipher.decrypt(
                                        service.getResource(Long.valueOf(rabbitId)).getConfig()))
                        .path("password")
                        .asText());
    }

    @Test
    @Order(2)
    void rejectsWrongDisabledMissingAndFractionalReferences() throws Exception {
        for (String invalid :
                new String[] {
                    config(resourceId, resourceId, "KAFKA"),
                    config(sourceId, rabbitId, "KAFKA"),
                    config(sourceId, "9007199254740993", "KAFKA"),
                    config("1.2", resourceId, "KAFKA"),
                    config("1e2", resourceId, "KAFKA")
                })
            assertTrue(
                    send("POST", "/api/tasks", task("PUSH", invalid), adminToken).statusCode()
                            >= 400);
        jdbc.update("UPDATE ifj_resource SET enabled=0 WHERE id=?", Long.valueOf(resourceId));
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                task("PUSH", config(sourceId, resourceId, "KAFKA")),
                                adminToken)
                        .statusCode());
        jdbc.update("UPDATE ifj_resource SET enabled=1 WHERE id=?", Long.valueOf(resourceId));
        assertEquals(
                409, send("DELETE", "/api/resources/" + resourceId, null, adminToken).statusCode());
        assertEquals(
                409, send("DELETE", "/api/resources/" + sourceId, null, adminToken).statusCode());
    }

    @Test
    @Order(3)
    void permissionsAndAuditDoNotExposeMqSecrets() throws Exception {
        assertEquals(
                403, send("GET", "/api/resources/" + rabbitId, null, viewerToken).statusCode());
        assertEquals(
                403,
                send("POST", "/api/resources/" + rabbitId + "/connection-test", null, operatorToken)
                        .statusCode());
        assertEquals(
                403,
                send(
                                "POST",
                                "/api/tasks",
                                task("PUSH", config(sourceId, resourceId, "KAFKA")),
                                operatorToken)
                        .statusCode());
        assertEquals(200, send("GET", "/api/resources?type=KAFKA", null, viewerToken).statusCode());
        var audit = send("GET", "/api/audits?pageSize=100", null, adminToken);
        assertEquals(200, audit.statusCode());
        assertTrue(!audit.body().contains("fixture-mq"));
    }

    @Test
    @Order(4)
    void publishAndDeleteCannotLeaveDanglingMessageResource() throws Exception {
        String id = createResource("KAFKA", "race", Map.of("bootstrapServers", "127.0.0.1:1"));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var publication =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                                "POST",
                                                "/api/tasks",
                                                task("PUSH", config(sourceId, id, "KAFKA")),
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
            int p = publication.get(15, TimeUnit.SECONDS), d = deletion.get(15, TimeUnit.SECONDS);
            assertTrue(
                    (p == 201 && d == 409) || (p == 404 && d == 200),
                    "Publish/delete: " + p + "/" + d);
        }
    }

    @Test
    @Order(5)
    void concurrentKeepAndReplaceCannotRestoreThePreviousPassword() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var keep =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                                "PUT",
                                                "/api/resources/" + rabbitId,
                                                Map.of(
                                                        "name",
                                                        "Kept",
                                                        "code",
                                                        "rabbit",
                                                        "type",
                                                        "RABBITMQ",
                                                        "rabbitConfig",
                                                        Map.of("preserveExisting", true)),
                                                adminToken)
                                        .statusCode();
                            });
            var replace =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                                "PUT",
                                                "/api/resources/" + rabbitId,
                                                Map.of(
                                                        "name",
                                                        "Replaced",
                                                        "code",
                                                        "rabbit",
                                                        "type",
                                                        "RABBITMQ",
                                                        "rabbitConfig",
                                                        Map.of(
                                                                "preserveExisting",
                                                                true,
                                                                "password",
                                                                "fixture-replaced")),
                                                adminToken)
                                        .statusCode();
                            });
            start.countDown();
            assertEquals(200, keep.get(15, TimeUnit.SECONDS));
            assertEquals(200, replace.get(15, TimeUnit.SECONDS));
        }
        var service = context.getBean(ResourceService.class);
        var cipher = context.getBean(ResourceConfigCipher.class);
        assertEquals(
                "fixture-replaced",
                json.readTree(
                                cipher.decrypt(
                                        service.getResource(Long.valueOf(rabbitId)).getConfig()))
                        .path("password")
                        .asText());
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
