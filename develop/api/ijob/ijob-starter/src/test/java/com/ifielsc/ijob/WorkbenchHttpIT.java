// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

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
class WorkbenchHttpIT {
    private static final String PASSWORD = "Workbench-fixture-password-123";
    private static final String WINDOW =
            "/api/workbench?from=2026-09-27T00:00:00&to=2026-09-28T00:00:00";
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
                        + directory.resolve("workbench.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=workbench-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=workbench-fixture",
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
    void rootCreationWindowIncludesBothEndpointsAndExcludesChildrenAndOutsideRows()
            throws Exception {
        var result = get(WINDOW, viewerToken);
        assertEquals("12", result.path("total").asText());
        assertTrue(result.path("total").isTextual());
        assertEquals("2", result.path("statuses").path("SUCCESS").asText());
        assertEquals("6", result.path("statuses").path("FAILED").asText());
        assertEquals("1", result.path("statuses").path("TIMED_OUT").asText());
        assertEquals("9", result.path("successRateDenominator").asText());
        assertEquals("22.22", result.path("successRatePercent").asText());
        // A running root older than this window still belongs to current backlog.
        assertEquals("1", result.path("currentActiveRoots").path("RUNNING").asText());
        assertEquals("1", result.path("currentActiveRoots").path("WAITING_EVENT").asText());
    }

    @Test
    void approvalTotalsRequireRoleAndSnapshotMembershipEvenForAdministrator() throws Exception {
        var viewer = get(WINDOW, viewerToken);
        assertFalse(viewer.path("approvalsVisible").asBoolean());
        assertTrue(
                viewer.path("pendingApprovals").isNull()
                        || viewer.path("pendingApprovals").isMissingNode());
        assertEquals("1", get(WINDOW, operatorToken).path("pendingApprovals").asText());
        assertEquals("0", get(WINDOW, adminToken).path("pendingApprovals").asText());
        // An unrelated operator, resolved approval and plain event are not pending approvals.
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_event_wait", Integer.class));
    }

    @Test
    void recentFailuresAreFiveStableHistoricalIdentitiesWithExactIdsAndNoSensitiveFields()
            throws Exception {
        var rows = get(WINDOW, viewerToken).path("recentFailures");
        assertEquals(5, rows.size());
        assertEquals("9007199254740999", rows.get(0).path("id").asText());
        assertEquals("9007199254740995", rows.get(4).path("id").asText());
        assertEquals("Published task name", rows.get(0).path("name").asText());
        assertTrue(rows.get(0).path("id").isTextual());
        assertFalse(rows.toString().contains("fixture-private"));
        for (JsonNode row : rows) {
            assertFalse(row.has("message"));
            assertFalse(row.has("inputVariables"));
            assertFalse(row.has("pluginConfig"));
        }
    }

    @Test
    void emptyWindowHasZerosAndNoInventedSuccessRate() throws Exception {
        var result =
                get("/api/workbench?from=2020-01-01T00:00:00&to=2020-01-02T00:00:00", viewerToken);
        assertEquals("0", result.path("total").asText());
        assertEquals("0", result.path("successRateDenominator").asText());
        assertTrue(
                result.path("successRatePercent").isNull()
                        || result.path("successRatePercent").isMissingNode());
        assertEquals(0, result.path("recentFailures").size());
    }

    @Test
    void apiOnlyMetadataReportsConfigurationWithoutClaimingClusterLiveness() throws Exception {
        var result = get("/api/workbench", viewerToken);
        assertEquals("UTC", result.path("timeZone").asText());
        assertEquals("America/New_York", result.path("businessZone").asText());
        var node = result.path("node");
        assertEquals("API", node.path("roles").get(0).asText());
        assertFalse(node.path("workerRoleConfigured").asBoolean());
        assertFalse(node.path("scheduleEnabled").asBoolean());
        assertEquals("UNKNOWN", node.path("clusterWorkerHealth").asText());
        assertFalse(node.toString().contains("key"));
    }

    @Test
    void rejectsAnonymousMalformedPartialReverseAndOversizedRanges() throws Exception {
        assertEquals(401, send("GET", WINDOW, null, null).statusCode());
        for (String query :
                new String[] {
                    "from=2026-09-27T00:00:00",
                    "from=bad&to=bad",
                    "from=2026-09-28T00:00:00&to=2026-09-27T00:00:00",
                    "from=2026-01-01T00:00:00&to=2026-09-27T00:00:00"
                }) {
            assertEquals(
                    400, send("GET", "/api/workbench?" + query, null, viewerToken).statusCode());
        }
    }

    @Test
    void rootAggregationUsesExistingHistoryIndex() {
        String plan =
                jdbc.queryForList(
                                "EXPLAIN QUERY PLAN SELECT status, COUNT(*) FROM ifj_instance WHERE"
                                    + " parent_instance_id IS NULL AND create_time >= '2026-09-27"
                                    + " 00:00:00.000' AND create_time <= '2026-09-28 00:00:00.000'"
                                    + " GROUP BY status")
                        .toString();
        assertTrue(plan.contains("idx_ifj_instance_root_history_v25"), plan);
    }

    private void fixtures() throws Exception {
        jdbc.update(
                "INSERT INTO ifj_task(id,name,plugin_type,plugin_config) VALUES(800,'Current task"
                        + " name','SQL','{}')");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES(801,800,1,'Published task name','SQL','{}',0,0,1)");
        instance(1, "SUCCESS", "2026-09-27 00:00:00.000", null);
        instance(2, "SUCCESS", "2026-09-28 00:00:00.000", null);
        instance(3, "TIMED_OUT", "2026-09-27 10:00:00.000", null);
        instance(4, "STOPPED", "2026-09-27 10:00:00.000", null);
        instance(5, "SKIPPED", "2026-09-27 10:00:00.000", null);
        instance(6, "WAITING_EVENT", "2026-09-27 10:00:00.000", null);
        instance(7, "FAILED", "2026-09-27 10:00:00.000", 1L);
        instance(8, "FAILED", "2026-09-26 23:59:59.999", null);
        instance(9, "FAILED", "2026-09-28 00:00:00.001", null);
        instance(10, "RUNNING", "2026-01-01 00:00:00.000", null);
        for (long id = 9007199254740994L; id <= 9007199254740999L; id++)
            instance(id, "FAILED", "2026-09-27 12:00:00.000", null);
        String operator = get("/api/auth/me", operatorToken).path("loginId").asText();
        assertFalse(operator.isBlank());
        for (int id = 100; id < 104; id++) {
            String type = id == 103 ? "EVENT" : "APPROVAL";
            String status = id == 102 ? "APPROVED" : "WAITING";
            String member = id == 101 ? "someone-else" : operator;
            jdbc.update(
                    "INSERT INTO"
                        + " ifj_event_wait(id,instance_id,root_instance_id,attempt_no,wait_type,status,accept_after,allowed_operator_ids,create_time)"
                        + " VALUES(?,6,6,?,?,?, ?,?,?)",
                    id,
                    id,
                    type,
                    status,
                    "2026-01-01 00:00:00.000",
                    json.writeValueAsString(java.util.List.of(member)),
                    "2026-01-01 00:00:00.000");
            jdbc.update(
                    "INSERT INTO ifj_event_wait_operator(wait_id,operator_id) VALUES(?,?)",
                    id,
                    member);
        }
    }

    private void instance(long id, String status, String created, Long parent) {
        jdbc.update(
                "INSERT INTO"
                    + " ifj_instance(id,root_instance_id,parent_instance_id,target_type,target_id,status,create_time,task_definition_id,message,input_variables)"
                    + " VALUES(?,?,?,'TASK',800,?,?,801,?,?)",
                id,
                parent == null ? id : parent,
                parent,
                status,
                created,
                "fixture-private-message",
                "fixture-private-variables");
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
