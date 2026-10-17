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
class UnifiedExperienceHttpIT {
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
                                        "--app.runtime.instance-name=unified-experience-http",
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
    void versionsArePagedExactAndSafeForEveryRole() throws Exception {
        for (String token : new String[] {adminToken, viewerToken, operatorToken}) {
            var result = get("/api/tasks/9007199254740993/versions?pageSize=1", token);
            assertEquals(2, result.path("total").asInt());
            assertEquals(1, result.path("list").size());
            var row = result.path("list").get(0);
            assertEquals("9007199254740995", row.path("definitionId").asText());
            assertEquals("2", row.path("revision").asText());
            assertTrue(row.path("current").asBoolean());
            assertFalse(result.toString().contains("private-old"));
            assertFalse(result.toString().contains("pluginConfig"));
            var old = get("/api/tasks/9007199254740993/versions?pageSize=1&pageNum=2", token);
            assertEquals("1", old.path("list").get(0).path("revision").asText());
            assertFalse(old.path("list").get(0).path("current").asBoolean());
        }
    }

    @Test
    void historicalDetailUsesItsOwnSnapshotAndGroups() throws Exception {
        var old = get("/api/tasks/9007199254740993/versions/9007199254740994", adminToken);
        assertEquals("Original", old.path("name").asText());
        assertTrue(old.path("pluginConfig").asText().contains("private-old"));
        assertEquals("old-group", old.path("groups").get(0).asText());
        assertEquals("9007199254740993", old.path("id").asText());
        assertEquals(
                404,
                send("GET", "/api/tasks/800/versions/9007199254740994", null, adminToken)
                        .statusCode());
        for (String token : new String[] {viewerToken, operatorToken})
            assertEquals(
                    403,
                    send(
                                    "GET",
                                    "/api/tasks/9007199254740993/versions/9007199254740994",
                                    null,
                                    token)
                            .statusCode());
        assertEquals(
                401, send("GET", "/api/tasks/9007199254740993/versions", null, null).statusCode());
    }

    @Test
    void listIdFiltersApplyBeforePaginationAndCombineWithOtherFilters() throws Exception {
        for (String token : new String[] {adminToken, viewerToken, operatorToken}) {
            var result = get("/api/tasks?id=9007199254740993&pageSize=1", token);
            assertEquals(1, result.path("total").asInt());
            assertEquals("9007199254740993", result.path("list").get(0).path("id").asText());
            assertEquals(
                    0,
                    get("/api/tasks?id=9007199254740993&name=missing", token)
                            .path("total")
                            .asInt());
            var jobs = get("/api/jobs?id=850", token);
            assertEquals(1, jobs.path("total").asInt());
            assertEquals(1, jobs.path("list").get(0).path("nodeCount").asInt());
            assertEquals(0, get("/api/jobs?id=850&name=missing", token).path("total").asInt());
        }
        assertEquals(400, send("GET", "/api/tasks?id=-1", null, adminToken).statusCode());
        assertEquals(
                400,
                send("GET", "/api/jobs?id=9223372036854775808", null, adminToken).statusCode());
    }

    private void fixtures() {
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task(id,name,plugin_type,plugin_config,current_task_definition_id,current_revision)"
                    + " VALUES(9007199254740993,'Current','SQL','{}',9007199254740995,2)");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task(id,name,plugin_type,plugin_config,current_task_definition_id,current_revision)"
                    + " VALUES(800,'Other','SQL','{}',801,1)");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES(9007199254740994,9007199254740993,1,'Original','SQL','{\"script\":\"private-old\"}',0,0,1)");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES(9007199254740995,9007199254740993,2,'Current','SQL','{}',0,0,1)");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_task_definition(id,task_id,revision,name,plugin_type,plugin_config,retry_times,retry_interval,enabled)"
                    + " VALUES(801,800,1,'Other','SQL','{}',0,0,1)");
        jdbc.update(
                "INSERT INTO ifj_task_definition_group(definition_id,group_code)"
                        + " VALUES(9007199254740994,'old-group')");
        jdbc.update(
                "INSERT INTO ifj_job(id,name,current_definition_id,current_revision)"
                        + " VALUES(850,'Job',851,1)");
        jdbc.update(
                "INSERT INTO ifj_job_definition(id,job_id,revision,name,enabled)"
                        + " VALUES(851,850,1,'Job',1)");
        jdbc.update(
                "INSERT INTO"
                    + " ifj_job_definition_node(id,definition_id,node_key,name,node_kind,gateway_mode)"
                    + " VALUES(852,851,'gate','Gate','GATEWAY','EXCLUSIVE')");
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
