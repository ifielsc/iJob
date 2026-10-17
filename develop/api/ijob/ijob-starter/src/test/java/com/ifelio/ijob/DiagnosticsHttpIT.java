package com.ifelio.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

/** Real HTTP, authorization, Flyway and mapper execution in an isolated SQLite database. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiagnosticsHttpIT {
    private static final String PASSWORD = "Diagnostics-fixture-password-123";
    @TempDir static Path directory;
    private ServletWebServerApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private JdbcTemplate jdbc;
    private String base;
    private String adminToken;
    private String operatorToken;
    private String viewerToken;

    @BeforeAll
    void start() throws Exception {
        String url =
                "jdbc:sqlite:"
                        + directory.resolve("diagnostics.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=diagnostics-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=diagnostics-fixture",
                                        "--app.security.aes.key="
                                                + java.util.Base64.getEncoder()
                                                        .encodeToString(new byte[32]),
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=fixture-admin",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4).encode(PASSWORD),
                                        "--app.schedule.enabled=false",
                                        "--app.schedule.zone-id=America/New_York",
                                        "--app.event.recovery.enabled=false",
                                        "--app.event.ingress.enabled=false",
                                        "--app.notification.outbox.enabled=false",
                                        "--app.retention.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--logging.level.root=OFF");
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        adminToken = login("fixture-admin");
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
                                            PASSWORD,
                                            "roleCode",
                                            role),
                                    adminToken)
                            .statusCode());
        }
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        viewerToken = login("viewer");
        operatorToken = login("operator");
        fixtures();
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
    }

    @Test
    void allThreeRolesReadTheSameSafeCoordinationFacts() throws Exception {
        for (String token : new String[] {adminToken, operatorToken, viewerToken}) {
            var result = get("/api/diagnostics", token);
            assertEquals("LEASE_EXPIRED", result.path("category").asText());
            assertEquals(2, result.path("items").size());
            assertEquals("9007199254740993", result.path("items").get(0).path("id").asText());
            assertTrue(result.path("items").get(0).path("id").isTextual());
            assertEquals("Published task", result.path("items").get(0).path("name").asText());
            assertFalse(result.toString().contains("fixture-private"));
            for (String field :
                    new String[] {
                        "claimToken",
                        "leaseOwner",
                        "lastError",
                        "variables",
                        "processingToken",
                        "message"
                    }) assertFalse(result.toString().contains(field));
        }
    }

    @Test
    void queueDelaysExcludeFutureExpiredExhaustedAndSuspendedWork() throws Exception {
        var result = get("/api/diagnostics?category=QUEUE_DELAYED", viewerToken);
        assertEquals(2, result.path("items").size());
        assertEquals("10", result.path("items").get(0).path("id").asText());
        assertEquals("11", result.path("items").get(1).path("id").asText());
        assertEquals(300, result.path("queueDelaySeconds").asInt());
    }

    @Test
    void deadlineExcludesChildrenTerminalAndFutureRoots() throws Exception {
        var result = get("/api/diagnostics?category=ROOT_DEADLINE_EXCEEDED", viewerToken);
        assertEquals(2, result.path("items").size());
        assertEquals("1", result.path("items").get(0).path("id").asText());
        assertEquals("2", result.path("items").get(1).path("id").asText());
    }

    @Test
    void expiredUnsettledCommandsExcludeAlreadyFinalAndFutureCommands() throws Exception {
        var result = get("/api/diagnostics?category=COMMAND_EXPIRED", viewerToken);
        assertEquals(2, result.path("items").size());
        assertEquals("cmd-0", result.path("items").get(0).path("id").asText());
        assertEquals("cmd-1", result.path("items").get(1).path("id").asText());
        assertEquals("STOP", result.path("items").get(0).path("commandType").asText());
        assertFalse(result.toString().contains("fixture-private"));
    }

    @Test
    void boundedStableResultsExposeHasMoreWithoutTotalAndRetainEmptySemantics() throws Exception {
        var result = get("/api/diagnostics?limit=1", viewerToken);
        assertEquals(1, result.path("items").size());
        assertTrue(result.path("hasMore").asBoolean());
        assertFalse(result.has("total"));
        assertFalse(get("/api/diagnostics?limit=2", viewerToken).path("hasMore").asBoolean());
        var mapper =
                context.getBean(com.ifelio.ijob.infra.mapper.diagnostics.DiagnosticsMapper.class);
        var before = java.time.LocalDateTime.of(2019, 1, 1, 0, 0);
        assertTrue(
                mapper.selectItems(
                                com.ifelio.ijob.contract.diagnostics.DiagnosticCategory
                                        .LEASE_EXPIRED,
                                before,
                                before)
                        .isEmpty());
    }

    @Test
    void exactDatabaseTimeBoundariesAreInclusive() {
        var mapper =
                context.getBean(com.ifelio.ijob.infra.mapper.diagnostics.DiagnosticsMapper.class);
        var now = java.time.LocalDateTime.of(2020, 1, 1, 0, 0);
        assertEquals(
                2,
                mapper.selectItems(
                                com.ifelio.ijob.contract.diagnostics.DiagnosticCategory
                                        .LEASE_EXPIRED,
                                now,
                                now.minusSeconds(300))
                        .size());
        assertTrue(
                mapper.selectItems(
                                com.ifelio.ijob.contract.diagnostics.DiagnosticCategory
                                        .LEASE_EXPIRED,
                                now.minusNanos(1000000),
                                now)
                        .isEmpty());
        assertEquals(
                2,
                mapper.selectItems(
                                com.ifelio.ijob.contract.diagnostics.DiagnosticCategory
                                        .QUEUE_DELAYED,
                                now.plusSeconds(300),
                                now)
                        .size());
        assertTrue(
                mapper.selectItems(
                                com.ifelio.ijob.contract.diagnostics.DiagnosticCategory
                                        .QUEUE_DELAYED,
                                now.plusSeconds(299),
                                now.minusSeconds(1))
                        .isEmpty());
    }

    @Test
    void databaseClockAndNodeConfigurationDoNotClaimClusterHealth() throws Exception {
        var result = get("/api/diagnostics", viewerToken);
        assertEquals("UTC", result.path("timeZone").asText());
        assertEquals("America/New_York", result.path("businessZone").asText());
        assertEquals("API", result.path("node").path("roles").get(0).asText());
        assertFalse(result.path("node").path("workerRoleConfigured").asBoolean());
        assertEquals("UNKNOWN", result.path("node").path("clusterWorkerHealth").asText());
        assertFalse(result.path("observedAt").asText().isBlank());
        assertEquals(
                "CLAIMED",
                jdbc.queryForObject(
                        "SELECT status FROM ifj_trigger_work_item WHERE"
                                + " trigger_id=9007199254740993",
                        String.class));
    }

    @Test
    void rejectsAnonymousUnknownCategoryAndOutOfBoundLimit() throws Exception {
        assertEquals(401, send("GET", "/api/diagnostics", null, null).statusCode());
        for (String query :
                new String[] {"limit=0", "limit=-1", "limit=101", "limit=bad", "category=OTHER"})
            assertEquals(
                    400, send("GET", "/api/diagnostics?" + query, null, viewerToken).statusCode());
    }

    private void fixtures() {
        jdbc.update(
                "INSERT INTO ifj_task(id,name,plugin_type,plugin_config) VALUES(800,'Current"
                        + " task','SQL','{}')");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES(801,800,1,'Published task','SQL','{}',0,0,1)");
        for (int i = 1; i <= 5; i++)
            jdbc.update(
                    "INSERT INTO"
                        + " ifj_instance(id,root_instance_id,parent_instance_id,target_type,target_id,status,timeout_at)"
                        + " VALUES(?,?,?,'TASK',800,?,?)",
                    i,
                    i == 3 ? 1 : i,
                    i == 3 ? 1 : null,
                    i == 4 ? "SUCCESS" : i == 2 ? "PAUSED" : "RUNNING",
                    i == 5 ? "2999-01-01 00:00:00.000" : "2020-01-01 00:00:00.000");
        work(9007199254740993L, "CLAIMED", false, false, false);
        work(9007199254740994L, "CLAIMED", false, false, false);
        work(9007199254740995L, "CLAIMED", true, false, false);
        work(10, "READY", false, false, false);
        work(11, "RETRY_WAIT", false, false, false);
        work(12, "READY", true, false, false);
        work(13, "READY", false, true, false);
        work(14, "READY", false, false, true);
        work(15, "SUSPENDED", false, false, false);
        String[] statuses = {"PENDING", "PROCESSING", "EXPIRED", "SUCCEEDED", "PENDING"};
        for (int i = 0; i < statuses.length; i++) {
            boolean processing = i == 1;
            jdbc.update(
                    "INSERT INTO"
                        + " ifj_runtime_command(request_id,instance_id,root_instance_id,trigger_id,command_type,status,requested_by,result_message,available_at,expires_at,processing_owner,processing_token,processing_fence)"
                        + " VALUES(?,1,1,10,'STOP',?,'fixture-private-actor','fixture-private-message',?,?,?,?,?)",
                    "cmd-" + i,
                    statuses[i],
                    "2020-01-01 00:00:00.000",
                    i == 4 ? "2999-01-01 00:00:00.000" : "2020-01-01 00:00:00.000",
                    processing ? "fixture-private-owner" : null,
                    processing ? "fixture-private-token" : null,
                    processing ? 1 : null);
        }
    }

    private void work(long id, String status, boolean future, boolean expired, boolean exhausted) {
        String date = future ? "2999-01-01 00:00:00.000" : "2020-01-01 00:00:00.000";
        jdbc.update(
                "INSERT INTO"
                    + " ifj_trigger(id,target_type,target_id,trigger_type,idempotency_key,status,trigger_time,task_definition_id,variables)"
                    + " VALUES(?,'TASK',800,'MANUAL',?,'WAITING',?,801,'fixture-private-variables')",
                id,
                "fixture-" + id,
                date);
        boolean claimed = status.equals("CLAIMED");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_trigger_work_item(trigger_id,status,available_at,max_attempts,attempt_count,expires_at,lease_owner,claim_token,lease_deadline,last_error)"
                    + " VALUES(?,?,?,3,?,?,?,?,?,'fixture-private-error')",
                id,
                status,
                date,
                exhausted ? 3 : 0,
                expired ? "2019-01-01 00:00:00.000" : null,
                claimed ? "fixture-private-owner" : null,
                claimed ? "fixture-private-" + id : null,
                claimed ? date : null);
    }

    private String login(String username) throws Exception {
        var response =
                send(
                        "POST",
                        "/api/auth/login",
                        Map.of("username", username, "password", PASSWORD),
                        null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("data").path("tokenValue").asText();
    }

    private JsonNode get(String path, String token) throws Exception {
        var response = send("GET", path, null, token);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> send(String method, String path, Object body, String token)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json");
        if (token != null) request.header("ijob-token", token);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
