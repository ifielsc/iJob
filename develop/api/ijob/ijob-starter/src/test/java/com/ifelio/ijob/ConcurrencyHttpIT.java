package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class ConcurrencyHttpIT {
    @TempDir static Path directory;
    private ServletWebServerApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private JdbcTemplate jdbc;
    private String base;
    private String adminToken;
    private String viewerToken;
    private String operatorToken;
    private String viewerId;
    private String resourceId;
    private String taskId;
    private String definitionId;
    private static final String INITIAL = "Http-initial-password-123";
    private static final String CHANGED = "Http-changed-password-123";

    @BeforeAll
    void start() throws Exception {
        String url =
                "jdbc:sqlite:"
                        + directory.resolve("concurrency-http.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=concurrency-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=concurrency-http-fixture",
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

    @Test
    @Order(1)
    void resourceRevisionAndAuditAreEnforced() throws Exception {
        var created =
                send(
                        "POST",
                        "/api/concurrency/resources",
                        Map.of("code", "report-pool", "name", "Report pool", "maxConcurrency", 8),
                        adminToken);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(data(created).isTextual());
        resourceId = data(created).asText();
        assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith(resourceId));
        var row = data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken));
        assertEquals("1", row.path("version").asText());
        assertTrue(row.path("enabled").asBoolean());
        var update =
                new java.util.HashMap<String, Object>(
                        Map.of("code", "report-pool", "name", "Changed", "maxConcurrency", 9));
        assertEquals(
                400,
                send("PUT", "/api/concurrency/resources/" + resourceId, update, adminToken)
                        .statusCode());
        update.put("expectedVersion", "1");
        assertEquals(
                200,
                send("PUT", "/api/concurrency/resources/" + resourceId, update, adminToken)
                        .statusCode());
        assertEquals(
                409,
                send("PUT", "/api/concurrency/resources/" + resourceId, update, adminToken)
                        .statusCode());
        assertEquals(
                409,
                send(
                                "DELETE",
                                "/api/concurrency/resources/" + resourceId + "?expectedVersion=1",
                                null,
                                adminToken)
                        .statusCode());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                            + " action_code='CONCURRENCY_POLICY_CREATE_RESOURCE' AND target_id=?"
                            + " AND result_code='SUCCESS'",
                        Integer.class,
                        resourceId));
    }

    @Test
    @Order(2)
    void rolesReadSafeContextsAndCannotWrite() throws Exception {
        for (String token : new String[] {viewerToken, operatorToken}) {
            for (String path :
                    new String[] {
                        "/resources/page",
                        "/resources/" + resourceId,
                        "/plugin-policies",
                        "/metadata"
                    })
                assertEquals(200, send("GET", "/api/concurrency" + path, null, token).statusCode());
            assertEquals(
                    403,
                    send("POST", "/api/concurrency/resources", Map.of("code", "forbidden"), token)
                            .statusCode());
            assertEquals(
                    403,
                    send(
                                    "PUT",
                                    "/api/concurrency/plugin-policies/SQL",
                                    Map.of("expectedVersion", "0", "maxConcurrency", 1),
                                    token)
                            .statusCode());
        }
        var result = send("GET", "/api/concurrency/metadata", null, viewerToken);
        assertEquals(17_000, data(result).path("permitWaitTimeoutMillis").asLong());
        assertEquals(11, data(result).path("plugins").size());
        assertEquals("API_NODE", data(result).path("configurationSource").asText());
        assertFalse(result.body().contains("aes"));
        assertFalse(result.body().contains("password"));
    }

    @Test
    @Order(3)
    void pluginRevisionSurvivesDeletionAndRecreation() throws Exception {
        String path = "/api/concurrency/plugin-policies/SQL";
        var body = Map.of("expectedVersion", "0", "maxConcurrency", 10);
        String first = data(send("PUT", path, body, adminToken)).path("version").asText();
        assertEquals(
                200,
                send("DELETE", path + "?expectedVersion=" + first, null, adminToken).statusCode());
        String second = data(send("PUT", path, body, adminToken)).path("version").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        assertEquals(
                409,
                send("PUT", path, Map.of("expectedVersion", first, "maxConcurrency", 2), adminToken)
                        .statusCode());
    }

    @Test
    @Order(4)
    void taskPolicyIsIndependentAndProtectsResources() throws Exception {
        var publication =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "Concurrency fixture",
                                "pluginType",
                                "EVENT",
                                "pluginConfig",
                                "{\"action\":\"PUBLISH\",\"eventCode\":\"concurrency.fixture\"}"),
                        adminToken);
        assertEquals(201, publication.statusCode(), publication.body());
        taskId = data(publication).path("taskId").asText();
        definitionId = data(publication).path("definitionId").asText();
        var before = data(send("GET", "/api/concurrency/tasks/" + taskId, null, viewerToken));
        assertEquals("0", before.path("version").asText());
        assertTrue(before.path("taskMaxConcurrency").isNull());
        var body =
                Map.of(
                        "expectedVersion",
                        "0",
                        "taskMaxConcurrency",
                        2,
                        "resourceIds",
                        java.util.List.of(resourceId, resourceId));
        var result = send("PUT", "/api/concurrency/tasks/" + taskId, body, adminToken);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(1, data(result).path("resources").size());
        assertEquals("Concurrency fixture", data(result).path("taskName").asText());
        assertFalse(result.body().contains("pluginConfig"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_task_definition WHERE task_id=?",
                        Integer.class,
                        taskId));
        assertEquals(
                409,
                send("PUT", "/api/concurrency/tasks/" + taskId, body, adminToken).statusCode());
        var resource =
                data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken));
        assertEquals(1, resource.path("referenceCount").asInt());
        assertEquals(
                409,
                send(
                                "DELETE",
                                "/api/concurrency/resources/"
                                        + resourceId
                                        + "?expectedVersion="
                                        + resource.path("version").asText(),
                                null,
                                adminToken)
                        .statusCode());
    }

    @Test
    @Order(5)
    void allActiveStatesBlockRestrictiveChangesButAllowResourceIncrease() throws Exception {
        long instanceId = 8000001L;
        jdbc.update(
                "INSERT INTO ifj_instance(id,target_type,target_id,task_definition_id,status)"
                        + " VALUES (?,'TASK',?,?,'WAITING')",
                instanceId,
                taskId,
                definitionId);
        for (String status : new String[] {"WAITING", "RUNNING", "PAUSED", "WAITING_EVENT"}) {
            jdbc.update("UPDATE ifj_instance SET status=? WHERE id=?", status, instanceId);
            var row =
                    data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken));
            assertEquals(1, row.path("activeInstanceCount").asInt());
            assertEquals(
                    409,
                    send(
                                    "PUT",
                                    "/api/concurrency/resources/" + resourceId,
                                    Map.of(
                                            "expectedVersion",
                                            row.path("version").asText(),
                                            "code",
                                            "report-pool",
                                            "name",
                                            "Lower",
                                            "maxConcurrency",
                                            1),
                                    adminToken)
                            .statusCode());
            assertEquals(
                    409,
                    send(
                                    "PUT",
                                    "/api/concurrency/tasks/" + taskId,
                                    Map.of(
                                            "expectedVersion",
                                            "1",
                                            "taskMaxConcurrency",
                                            2,
                                            "resourceIds",
                                            java.util.List.of(resourceId)),
                                    adminToken)
                            .statusCode());
            assertEquals(
                    409,
                    send(
                                    "PUT",
                                    "/api/concurrency/plugin-policies/EVENT",
                                    Map.of("expectedVersion", "0", "maxConcurrency", 10),
                                    adminToken)
                            .statusCode());
            var meta = data(send("GET", "/api/concurrency/metadata", null, viewerToken));
            for (var item : meta.path("plugins"))
                if (item.path("type").asText().equals("EVENT"))
                    assertEquals(1, item.path("activeInstanceCount").asInt());
        }
        var row = data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken));
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/concurrency/resources/" + resourceId,
                                Map.of(
                                        "expectedVersion",
                                        row.path("version").asText(),
                                        "code",
                                        "report-pool",
                                        "name",
                                        "Increased",
                                        "maxConcurrency",
                                        10),
                                adminToken)
                        .statusCode());
        jdbc.update("UPDATE ifj_instance SET status='SUCCESS' WHERE id=?", instanceId);
    }

    @Test
    @Order(6)
    void disabledResourcesAreRetainedAndMustBeExplicitlyRemoved() throws Exception {
        var row = data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken));
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/concurrency/resources/" + resourceId,
                                Map.of(
                                        "expectedVersion",
                                        row.path("version").asText(),
                                        "code",
                                        "report-pool",
                                        "name",
                                        "Disabled",
                                        "maxConcurrency",
                                        10,
                                        "enabled",
                                        false),
                                adminToken)
                        .statusCode());
        assertFalse(
                data(send("GET", "/api/concurrency/tasks/" + taskId, null, viewerToken))
                        .path("resources")
                        .get(0)
                        .path("enabled")
                        .asBoolean());
        assertEquals(
                409,
                send(
                                "PUT",
                                "/api/concurrency/tasks/" + taskId,
                                Map.of(
                                        "expectedVersion",
                                        "1",
                                        "taskMaxConcurrency",
                                        2,
                                        "resourceIds",
                                        java.util.List.of(resourceId)),
                                adminToken)
                        .statusCode());
        var clear = new java.util.HashMap<String, Object>();
        clear.put("expectedVersion", "1");
        clear.put("taskMaxConcurrency", null);
        clear.put("resourceIds", java.util.List.of());
        assertEquals(
                200,
                send("PUT", "/api/concurrency/tasks/" + taskId, clear, adminToken).statusCode());
        assertEquals(
                0,
                data(send("GET", "/api/concurrency/resources/" + resourceId, null, adminToken))
                        .path("referenceCount")
                        .asInt());
    }

    @Test
    @Order(7)
    void inputBoundsAndFractionalNumbersAreRejected() throws Exception {
        for (Object max : java.util.List.of(0, 10001, 1.5, "3"))
            assertEquals(
                    400,
                    send(
                                    "POST",
                                    "/api/concurrency/resources",
                                    Map.of(
                                            "code",
                                            "invalid",
                                            "name",
                                            "Invalid",
                                            "maxConcurrency",
                                            max),
                                    adminToken)
                            .statusCode());
        for (String code : java.util.List.of("bad space", "x".repeat(129)))
            assertEquals(
                    400,
                    send(
                                    "POST",
                                    "/api/concurrency/resources",
                                    Map.of("code", code, "name", "Invalid", "maxConcurrency", 1),
                                    adminToken)
                            .statusCode());
        for (int max : new int[] {1, 10000})
            assertEquals(
                    201,
                    send(
                                    "POST",
                                    "/api/concurrency/resources",
                                    Map.of(
                                            "code",
                                            "bound-" + max,
                                            "name",
                                            "Bound",
                                            "maxConcurrency",
                                            max),
                                    adminToken)
                            .statusCode());
        assertEquals(
                400,
                send(
                                "PUT",
                                "/api/concurrency/plugin-policies/SCRIPT",
                                Map.of("expectedVersion", 1.5, "maxConcurrency", 3),
                                adminToken)
                        .statusCode());
    }

    @Test
    @Order(8)
    void exactResourceIdsAndVersionsRoundTrip() throws Exception {
        jdbc.update(
                "INSERT INTO ifj_concurrency_resource(id,code,name,max_concurrency,enabled,version)"
                        + " VALUES (9223372036854775806,'exact','Exact',1,1,9007199254740993)");
        var row =
                data(
                        send(
                                "GET",
                                "/api/concurrency/resources/9223372036854775806",
                                null,
                                viewerToken));
        assertEquals("9223372036854775806", row.path("id").textValue());
        assertEquals("9007199254740993", row.path("version").textValue());
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/concurrency/resources/9223372036854775806",
                                Map.of(
                                        "expectedVersion",
                                        "9007199254740993",
                                        "code",
                                        "exact",
                                        "name",
                                        "Exact changed",
                                        "maxConcurrency",
                                        2),
                                adminToken)
                        .statusCode());
        assertEquals(
                "9007199254740994",
                data(send(
                                "GET",
                                "/api/concurrency/resources/9223372036854775806",
                                null,
                                viewerToken))
                        .path("version")
                        .asText());
    }

    @Test
    @Order(9)
    void pagedResourcesAndExactRecoveryRemainSafeForEveryReader() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    201,
                    send(
                                    "POST",
                                    "/api/concurrency/resources",
                                    Map.of(
                                            "code",
                                            "review-page-" + i,
                                            "name",
                                            "Paged resource " + i,
                                            "maxConcurrency",
                                            2,
                                            "enabled",
                                            i != 2),
                                    adminToken)
                            .statusCode());
        }
        for (String token : new String[] {adminToken, viewerToken, operatorToken}) {
            var removed = send("GET", "/api/concurrency/resources", null, token);
            assertEquals(405, removed.statusCode(), removed.body());
            assertTrue(removed.headers().firstValue("Allow").orElse("").contains("POST"));
            var response =
                    send(
                            "GET",
                            "/api/concurrency/resources/page?keyword=review-page&pageNum=2&pageSize=1",
                            null,
                            token);
            assertEquals(200, response.statusCode(), response.body());
            var page = data(response);
            assertEquals(3, page.path("total").asInt());
            assertEquals(2, page.path("pageNum").asInt());
            assertEquals(1, page.path("pageSize").asInt());
            assertEquals(3, page.path("pages").asInt());
            assertEquals(1, page.path("list").size());
            assertEquals("review-page-1", page.path("list").get(0).path("code").asText());
            assertTrue(page.path("list").get(0).path("id").isTextual());
            assertTrue(page.path("list").get(0).has("referenceCount"));
            var enabled =
                    data(
                            send(
                                    "GET",
                                    "/api/concurrency/resources/page?keyword=review-page&enabled=true",
                                    null,
                                    token));
            assertEquals(2, enabled.path("total").asInt());
            var exact =
                    send(
                            "GET",
                            "/api/concurrency/resources/by-code?code=review-page-2",
                            null,
                            token);
            assertEquals(200, exact.statusCode());
            var id = data(exact).path("id").asText();
            var recovered =
                    data(
                            send(
                                    "GET",
                                    "/api/concurrency/resources/by-ids?ids="
                                            + id
                                            + ",9223372036854775805",
                                    null,
                                    token));
            assertEquals(1, recovered.size());
            assertEquals(id, recovered.get(0).path("id").asText());
            assertEquals(
                    0, data(send("GET", "/api/concurrency/resources/by-ids", null, token)).size());
            assertEquals(
                    404,
                    send(
                                    "GET",
                                    "/api/concurrency/resources/by-code?code=review-absent",
                                    null,
                                    token)
                            .statusCode());
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
