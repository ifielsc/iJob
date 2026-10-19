// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.api.security.ApiAccess;
import com.ifielsc.ijob.api.security.AuditOperation;

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
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import javax.sql.DataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityHttpIT {
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
    private static final String INITIAL = "Http-initial-password-123";
    private static final String CHANGED = "Http-changed-password-123";

    @Test
    @Order(0)
    void existingAdministratorInitializationMustNotConsumeAnotherAccountId() {
        Long before =
                jdbc.queryForObject(
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_auth_user'",
                        Long.class);
        context.getBean(com.ifielsc.ijob.core.auth.AccountService.class).initialize();
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_auth_user'",
                        Long.class));
    }

    @BeforeAll
    void start() {
        String url =
                "jdbc:sqlite:"
                        + directory.resolve("security-http.db").toAbsolutePath()
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
                                        "--app.runtime.instance-name=security-http",
                                        "--app.runtime.roles=API",
                                        "--app.auth.mode=DATABASE",
                                        "--app.security.aes.key-id=security-http-fixture",
                                        "--app.security.aes.key="
                                                + java.util.Base64.getEncoder()
                                                        .encodeToString(new byte[32]),
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=legacy-admin",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4).encode(INITIAL),
                                        "--app.schedule.enabled=false",
                                        "--app.event.recovery.enabled=false",
                                        "--app.event.ingress.enabled=true",
                                        "--app.retention.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--logging.level.root=OFF");
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    @AfterAll
    void stop() {
        if (context != null) context.close();
    }

    @Test
    @Order(1)
    void allRegisteredApiMethodsDeclareAccessAndEveryCommandDeclaresAudit() {
        var mappings =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                        .getHandlerMethods();
        long count = 0;
        for (var entry : mappings.entrySet()) {
            if (entry.getKey().getPatternValues().stream().noneMatch(p -> p.startsWith("/api/")))
                continue;
            count++;
            var policy = entry.getValue().getMethodAnnotation(ApiAccess.class);
            assertNotNull(policy, entry.getKey().toString());
            for (var role : com.ifielsc.ijob.contract.auth.AccountRole.values()) {
                var actor =
                        new com.ifielsc.ijob.contract.auth.CurrentUserResponse(
                                "matrix",
                                "matrix",
                                "matrix",
                                role,
                                com.ifielsc.ijob.core.auth.AccountRolePolicy.permissions(role),
                                false);
                var properties = new com.ifielsc.ijob.common.config.AppProperties();
                properties
                        .getAuth()
                        .setMode(com.ifielsc.ijob.common.config.AppProperties.AuthMode.DATABASE);
                var stub =
                        new com.ifielsc.ijob.core.auth.AuthService(properties, null, null) {
                            @Override
                            public com.ifielsc.ijob.contract.auth.CurrentUserResponse
                                    currentUser() {
                                return actor;
                            }
                        };
                var interceptor =
                        new com.ifielsc.ijob.api.security.ApiSecurityInterceptor(
                                properties,
                                stub,
                                context.getBean(com.ifielsc.ijob.core.auth.AuditService.class));
                boolean allowed =
                        policy.publicEndpoint()
                                || policy.selfService()
                                || actor.getPermissions().contains(policy.value());
                if (allowed)
                    assertTrue(
                            interceptor.preHandle(
                                    new org.springframework.mock.web.MockHttpServletRequest(),
                                    new org.springframework.mock.web.MockHttpServletResponse(),
                                    entry.getValue()));
                else
                    org.junit.jupiter.api.Assertions.assertThrows(
                            com.ifielsc.ijob.common.exception.BusinessException.class,
                            () ->
                                    interceptor.preHandle(
                                            new org.springframework.mock.web
                                                    .MockHttpServletRequest(),
                                            new org.springframework.mock.web
                                                    .MockHttpServletResponse(),
                                            entry.getValue()));
            }
            var methods = entry.getKey().getMethodsCondition().getMethods();
            boolean query =
                    methods.stream().allMatch(m -> m.name().equals("GET"))
                            || entry.getKey().getPatternValues().contains("/api/schedules/preview")
                            || entry.getKey()
                                    .getPatternValues()
                                    .contains("/api/notification-preview")
                            || entry.getKey().getPatternValues().contains("/api/jobs/validate")
                            || entry.getKey()
                                    .getPatternValues()
                                    .contains("/api/reference-summaries")
                            || entry.getKey()
                                    .getPatternValues()
                                    .contains("/api/users/approvers/resolve")
                            || entry.getKey().getPatternValues().contains("/api/auth/login")
                            || entry.getKey().getPatternValues().contains("/api/auth/logout");
            if (!query)
                assertNotNull(
                        entry.getValue().getMethodAnnotation(AuditOperation.class),
                        entry.getKey().toString());
        }
        // Includes three concurrency paging/reconciliation reads; the legacy full-list GET is
        // removed.
        assertEquals(140, count);
    }

    @Test
    @Order(2)
    void initialPasswordWhitelistThenRealHeaderSessionAndDefaults() throws Exception {
        adminToken = login("legacy-admin", INITIAL);
        assertTrue(
                data(send("GET", "/api/auth/me", null, adminToken))
                        .path("mustChangePassword")
                        .asBoolean());
        assertEquals(403, send("GET", "/api/tasks", null, adminToken).statusCode());
        assertEquals(403, send("GET", "/api/tasks/groups", null, adminToken).statusCode());
        assertEquals(
                200,
                send(
                                "POST",
                                "/api/auth/change-password",
                                Map.of("currentPassword", INITIAL, "newPassword", CHANGED),
                                adminToken)
                        .statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, adminToken).statusCode());
        adminToken = login("legacy-admin", CHANGED);
        JsonNode me = data(send("GET", "/api/auth/me", null, adminToken));
        assertEquals("legacy-admin", me.path("loginId").asText());
        assertEquals("ADMIN", me.path("roleCode").asText());
        assertFalse(me.path("mustChangePassword").asBoolean());
        assertTrue(me.path("permissions").size() > 0);
        var created =
                send(
                        "POST",
                        "/api/users",
                        Map.of("username", "viewer-user", "password", INITIAL),
                        adminToken);
        assertEquals(200, created.statusCode());
        JsonNode viewer = data(created);
        viewerId = viewer.path("id").asText();
        assertEquals("VIEWER", viewer.path("roleCode").asText());
        assertTrue(viewer.path("enabled").asBoolean());
        assertTrue(viewer.path("mustChangePassword").asBoolean());
        assertFalse(created.body().contains("passwordHash"));
        assertEquals(
                200,
                send(
                                "POST",
                                "/api/users",
                                Map.of(
                                        "username",
                                        "operator-user",
                                        "password",
                                        INITIAL,
                                        "roleCode",
                                        "OPERATOR"),
                                adminToken)
                        .statusCode());
        jdbc.update(
                "UPDATE ifj_auth_user SET must_change_password=0 WHERE username IN"
                        + " ('viewer-user','operator-user')");
        viewerToken = login("viewer-user", INITIAL);
        operatorToken = login("operator-user", INITIAL);
    }

    @Test
    @Order(3)
    void previewUsesRealTemplatesWithNoDeliveryOrQuotaSideEffects() throws Exception {
        var request = Map.of("type", "WECOM", "source", "task");
        assertEquals(401, send("POST", "/api/notification-preview", request, null).statusCode());
        assertEquals(
                403, send("POST", "/api/notification-preview", request, viewerToken).statusCode());
        int outbox =
                jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notification_outbox", Integer.class);
        int quota =
                jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notify_rate_limit", Integer.class);
        int logs = jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notify_send_log", Integer.class);
        var response = send("POST", "/api/notification-preview", request, adminToken);
        assertEquals(200, response.statusCode());
        assertEquals("markdown", data(response).path("payload").path("msgtype").asText());
        assertTrue(
                data(response)
                        .path("payload")
                        .path("markdown")
                        .path("content")
                        .asText()
                        .contains("46101"));
        assertFalse(response.body().contains("webhook"));
        assertEquals(
                outbox,
                jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notification_outbox", Integer.class));
        assertEquals(
                quota,
                jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notify_rate_limit", Integer.class));
        assertEquals(
                logs,
                jdbc.queryForObject("SELECT COUNT(*) FROM ifj_notify_send_log", Integer.class));
    }

    @Test
    @Order(3)
    void directHttpRoleDenialsHaveNoMutationAndSafeReadProjections() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM ifj_task", Integer.class);
        for (String token : new String[] {viewerToken, operatorToken}) {
            assertEquals(
                    403,
                    send("POST", "/api/resources/ssh/host-key", Map.of("host", "127.0.0.1"), token)
                            .statusCode());
            assertEquals(
                    403,
                    send("POST", "/api/tasks", Map.of("name", "not-created"), token).statusCode());
            assertEquals(403, send("GET", "/api/tasks/1", null, token).statusCode());
            assertEquals(403, send("GET", "/api/users", null, token).statusCode());
            assertEquals(403, send("GET", "/api/audits", null, token).statusCode());
            assertEquals(403, send("GET", "/api/instances/1/logs", null, token).statusCode());
            assertEquals(
                    403, send("GET", "/api/instances/1/logs/snapshot", null, token).statusCode());
            assertEquals(200, send("GET", "/api/tasks", null, token).statusCode());
            assertEquals(200, send("GET", "/api/tasks/groups", null, token).statusCode());
        }
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM ifj_task", Integer.class));
        assertEquals(
                403,
                send("POST", "/api/tasks/987654/manual-trigger", Map.of(), viewerToken)
                        .statusCode());
        assertEquals(
                404,
                send("POST", "/api/tasks/987654/manual-trigger", Map.of(), operatorToken)
                        .statusCode());
        assertEquals(403, send("GET", "/api/event-waits", null, viewerToken).statusCode());
        assertEquals(200, send("GET", "/api/event-waits", null, operatorToken).statusCode());
        assertEquals(401, send("GET", "/api/tasks", null, null).statusCode());
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES"
                        + " (800001,'safe-summary','EVENT','{}')");
        jdbc.update(
                "INSERT INTO ifj_schedule"
                        + " (id,target_type,target_id,schedule_type,interval_sec,variables) VALUES"
                        + " (800001,'TASK',800001,'INTERVAL',60,?)",
                "{\"credential\":\"DO_NOT_LEAK\"}");
        var safe = send("GET", "/api/schedules/800001", null, viewerToken);
        assertEquals(200, safe.statusCode());
        assertFalse(safe.body().contains("DO_NOT_LEAK"));
        assertFalse(data(safe).has("variables"));
        assertTrue(
                send("GET", "/api/schedules/800001", null, adminToken)
                        .body()
                        .contains("DO_NOT_LEAK"));
    }

    @Test
    @Order(4)
    void parameterTemplatesUseExactIdsSharedReadsAndExplicitBindings() throws Exception {
        var fields =
                java.util.List.of(
                        Map.of(
                                "name",
                                "batchSize",
                                "type",
                                "INTEGER",
                                "hasDefault",
                                true,
                                "defaultValue",
                                1000));
        var body = Map.of("name", "HTTP parameter template", "fields", fields);
        assertEquals(
                403, send("POST", "/api/parameter-templates", body, operatorToken).statusCode());
        var created = send("POST", "/api/parameter-templates", body, adminToken);
        assertEquals(201, created.statusCode(), created.body());
        String id = data(created).asText();
        assertTrue(id.matches("[1-9][0-9]*"));
        String path = "/api/parameter-templates/" + id;
        for (String token : java.util.List.of(viewerToken, operatorToken)) {
            var read = send("GET", path, null, token);
            assertEquals(200, read.statusCode());
            assertEquals(id, data(read).path("id").textValue());
            assertEquals(1000, data(read).path("fields").get(0).path("defaultValue").asInt());
            assertEquals(403, send("DELETE", path, null, token).statusCode());
        }
        String binding = "/api/parameter-templates/bindings/TASK/800001";
        assertEquals(200, send("PUT", binding, Map.of("templateId", id), adminToken).statusCode());
        assertEquals(id, data(send("GET", binding, null, operatorToken)).path("id").asText());
        var update = Map.of("name", "Updated template", "fields", fields, "revision", "1");
        assertEquals(200, send("PUT", path, update, adminToken).statusCode());
        assertEquals(409, send("PUT", path, update, adminToken).statusCode());
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/parameter-templates",
                                Map.of(
                                        "name",
                                        "Sensitive",
                                        "fields",
                                        java.util.List.of(
                                                Map.of(
                                                        "name",
                                                        "secret",
                                                        "type",
                                                        "STRING",
                                                        "sensitive",
                                                        true,
                                                        "hasDefault",
                                                        true,
                                                        "defaultValue",
                                                        "never-save"))),
                                adminToken)
                        .statusCode());
        assertEquals(200, send("DELETE", path, null, adminToken).statusCode());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_parameter_binding WHERE template_id=?",
                        Integer.class,
                        Long.valueOf(id)));
        assertEquals(404, send("GET", path, null, adminToken).statusCode());
    }

    @Test
    @Order(3)
    void parameterTemplateBatchDeletionIsValidatedAuthorizedAndAtomic() throws Exception {
        String collection = "/api/parameter-templates";
        String first =
                data(send(
                                "POST",
                                collection,
                                Map.of("name", "Batch template one", "fields", java.util.List.of()),
                                adminToken))
                        .asText();
        String second =
                data(send(
                                "POST",
                                collection,
                                Map.of("name", "Batch template two", "fields", java.util.List.of()),
                                adminToken))
                        .asText();
        var ids = Map.of("ids", java.util.List.of(first, second));
        String batch = collection + "/batch";
        assertEquals(403, send("DELETE", batch, ids, viewerToken).statusCode());
        assertEquals(403, send("DELETE", batch, ids, operatorToken).statusCode());
        assertEquals(
                400,
                send("DELETE", batch, Map.of("ids", java.util.List.of()), adminToken).statusCode());
        assertEquals(
                400,
                send("DELETE", batch, Map.of("ids", java.util.List.of(first, first)), adminToken)
                        .statusCode());
        assertEquals(
                400,
                send("DELETE", batch, Map.of("ids", java.util.List.of("0")), adminToken)
                        .statusCode());
        assertEquals(
                400,
                send(
                                "DELETE",
                                batch,
                                Map.of(
                                        "ids",
                                        java.util.stream.LongStream.rangeClosed(1, 101)
                                                .boxed()
                                                .toList()),
                                adminToken)
                        .statusCode());
        assertEquals(
                404,
                send(
                                "DELETE",
                                batch,
                                Map.of("ids", java.util.List.of(first, "9223372036854775806")),
                                adminToken)
                        .statusCode());
        assertEquals(200, send("GET", collection + "/" + first, null, viewerToken).statusCode());
        jdbc.update(
                "INSERT INTO ifj_task (id,name,plugin_type,plugin_config) VALUES"
                        + " (800002,'batch-template-target','EVENT','{}')");
        String binding = "/api/parameter-templates/bindings/TASK/800002";
        assertEquals(
                200, send("PUT", binding, Map.of("templateId", first), adminToken).statusCode());
        jdbc.execute(
                "CREATE TRIGGER test_parameter_delete_failure BEFORE DELETE ON"
                        + " ifj_parameter_template WHEN OLD.id="
                        + second
                        + " BEGIN SELECT RAISE(ABORT,'blocked'); END");
        try {
            assertTrue(send("DELETE", batch, ids, adminToken).statusCode() >= 400);
            assertEquals(
                    200, send("GET", collection + "/" + first, null, viewerToken).statusCode());
            assertEquals(
                    200, send("GET", collection + "/" + second, null, viewerToken).statusCode());
            assertEquals(first, data(send("GET", binding, null, viewerToken)).path("id").asText());
        } finally {
            jdbc.execute("DROP TRIGGER test_parameter_delete_failure");
        }
        assertEquals(200, send("DELETE", batch, ids, adminToken).statusCode());
        assertEquals(404, send("GET", collection + "/" + first, null, viewerToken).statusCode());
        assertEquals(404, send("GET", collection + "/" + second, null, viewerToken).statusCode());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_parameter_binding WHERE template_id=?",
                        Integer.class,
                        Long.valueOf(first)));
        // The shared batch audit records one summary and one entry per deleted ID.
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                + " action_code='PARAMETER_TEMPLATE_DELETE_BATCH' AND"
                                + " result_code='SUCCESS'",
                        Integer.class));
    }

    @Test
    @Order(4)
    void accountWritesAreAtomicWithAuditAndFailuresAreRecordedWithoutSecrets() throws Exception {
        long audits =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                + " action_code='ACCOUNT_CREATE' AND result_code='SUCCESS'",
                        Long.class);
        assertEquals(2, audits);
        jdbc.execute(
                "CREATE TRIGGER test_audit_failure BEFORE INSERT ON ifj_operation_audit WHEN"
                    + " NEW.action_code='ACCOUNT_CREATE' AND NEW.result_code='SUCCESS' BEGIN SELECT"
                    + " RAISE(ABORT,'injected audit outage'); END");
        try {
            assertEquals(
                    500,
                    send(
                                    "POST",
                                    "/api/users",
                                    Map.of("username", "rollback-account", "password", INITIAL),
                                    adminToken)
                            .statusCode());
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ifj_auth_user WHERE username='rollback-account'",
                            Integer.class));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                    + " action_code='ACCOUNT_CREATE' AND result_code='FAILED'",
                            Integer.class));
        } finally {
            jdbc.execute("DROP TRIGGER test_audit_failure");
        }
        var auditResponse = send("GET", "/api/audits", null, adminToken);
        assertEquals(200, auditResponse.statusCode());
        assertFalse(auditResponse.body().contains(INITIAL));
        assertFalse(auditResponse.body().contains("$2"));
        assertFalse(auditResponse.body().contains("DO_NOT_LEAK"));
        for (String direction : java.util.List.of("asc", "desc")) {
            var sorted =
                    send(
                            "GET",
                            "/api/audits?sortBy=occurredAt&sortDirection=" + direction,
                            null,
                            adminToken);
            assertEquals(200, sorted.statusCode());
        }
        for (String invalid :
                java.util.List.of(
                        "sortBy=actorName",
                        "sortDirection=ASC",
                        "sortBy=",
                        "sortDirection=desc%2Cid")) {
            assertEquals(400, send("GET", "/api/audits?" + invalid, null, adminToken).statusCode());
        }
    }

    @Test
    @Order(7)
    void roleChangeDisableAndResetInvalidateActualOldTokens() throws Exception {
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/users/" + viewerId + "/access",
                                Map.of("expectedRevision", 0, "roleCode", "OPERATOR"),
                                adminToken)
                        .statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, viewerToken).statusCode());
        viewerToken = login("viewer-user", INITIAL);
        assertEquals(
                "OPERATOR",
                data(send("GET", "/api/auth/me", null, viewerToken)).path("roleCode").asText());
        assertEquals(
                200,
                send(
                                "POST",
                                "/api/users/" + viewerId + "/reset-password",
                                Map.of("expectedRevision", 1, "newPassword", CHANGED),
                                adminToken)
                        .statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, viewerToken).statusCode());
        viewerToken = login("viewer-user", CHANGED);
        assertEquals(403, send("GET", "/api/tasks", null, viewerToken).statusCode());
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/users/" + viewerId + "/access",
                                Map.of("expectedRevision", 2, "enabled", false),
                                adminToken)
                        .statusCode());
        assertEquals(401, send("GET", "/api/auth/me", null, viewerToken).statusCode());
        assertEquals(
                401,
                send(
                                "POST",
                                "/api/auth/login",
                                Map.of("username", "viewer-user", "password", CHANGED),
                                null)
                        .statusCode());
    }

    @Test
    @Order(8)
    void loginAuditFailureDoesNotIssueSessionAndLogoutPrioritizesRevocation() throws Exception {
        jdbc.execute(
                "CREATE TRIGGER test_login_audit_failure BEFORE INSERT ON ifj_operation_audit WHEN"
                        + " NEW.action_code IN ('AUTH_LOGIN','AUTH_LOGOUT') BEGIN SELECT"
                        + " RAISE(ABORT,'injected audit outage'); END");
        try {
            var failed =
                    send(
                            "POST",
                            "/api/auth/login",
                            Map.of("username", "operator-user", "password", INITIAL),
                            null);
            assertEquals(500, failed.statusCode());
            assertFalse(failed.body().contains("tokenValue"));
            assertEquals(200, send("POST", "/api/auth/logout", Map.of(), adminToken).statusCode());
            assertEquals(401, send("GET", "/api/auth/me", null, adminToken).statusCode());
        } finally {
            jdbc.execute("DROP TRIGGER test_login_audit_failure");
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

    @Test
    @Order(4)
    void publishedTaskAndEventReplaysAreAcceptedOnceAndNeverReportedAsExecutionSuccess()
            throws Exception {
        var serviceCreated =
                send(
                        "POST",
                        "/api/resources",
                        Map.of(
                                "name",
                                "context-service",
                                "code",
                                "context-service",
                                "type",
                                "SERVICE",
                                "enabled",
                                true,
                                "serviceConfig",
                                Map.of(
                                        "protocol",
                                        "HTTP",
                                        "endpoint",
                                        "http://127.0.0.1:9/HIDDEN_ENDPOINT",
                                        "headers",
                                        Map.of("X-Service", "HIDDEN_SERVICE_HEADER"))),
                        adminToken);
        assertEquals(201, serviceCreated.statusCode(), serviceCreated.body());
        String serviceId = data(serviceCreated).asText();
        var apiCreated =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "api-context",
                                "pluginType",
                                "API_CALL",
                                "pluginConfig",
                                json.writeValueAsString(
                                        Map.of(
                                                "resourceId",
                                                serviceId,
                                                "method",
                                                "POST",
                                                "body",
                                                Map.of("test", "HIDDEN_BODY"),
                                                "headers",
                                                Map.of("X-Task", "HIDDEN_TASK_HEADER"),
                                                "remoteStop",
                                                Map.of(
                                                        "path",
                                                        "/stop",
                                                        "headers",
                                                        Map.of("X-Stop", "HIDDEN_STOP_HEADER"),
                                                        "body",
                                                        Map.of("id", "HIDDEN_STOP_BODY"))))),
                        adminToken);
        assertEquals(201, apiCreated.statusCode(), apiCreated.body());
        String apiId = data(apiCreated).path("taskId").asText();
        for (String token : new String[] {viewerToken, operatorToken, adminToken}) {
            var context = send("GET", "/api/tasks/" + apiId + "/execution-context", null, token);
            assertEquals(200, context.statusCode(), context.body());
            assertEquals("HTTP", data(context).path("protocol").asText());
            assertEquals("POST", data(context).path("method").asText());
            assertEquals(serviceId, data(context).path("serviceResourceId").asText());
            assertFalse(context.body().contains("HIDDEN_"));
        }
        assertEquals(403, send("GET", "/api/tasks/" + apiId, null, operatorToken).statusCode());
        var apiDetail = send("GET", "/api/tasks/" + apiId, null, adminToken);
        assertEquals(200, apiDetail.statusCode());
        assertFalse(apiDetail.body().contains("HIDDEN_TASK_HEADER"));
        assertFalse(apiDetail.body().contains("HIDDEN_STOP_HEADER"));
        assertEquals("X-Stop", data(apiDetail).path("remoteStopHeaderNames").get(0).asText());
        assertEquals("X-Task", data(apiDetail).path("headerNames").get(0).asText());
        var serviceDetail = send("GET", "/api/resources/" + serviceId, null, adminToken);
        assertEquals(
                "http://127.0.0.1:9/HIDDEN_ENDPOINT",
                data(serviceDetail).path("config").path("endpoint").asText());
        assertFalse(serviceDetail.body().contains("HIDDEN_SERVICE_HEADER"));
        String plugin =
                json.writeValueAsString(
                        Map.of(
                                "eventCode",
                                "SECURITY_TEST",
                                "payload",
                                Map.of("secret", "HIDDEN_PAYLOAD")));
        var created =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "audited-task",
                                "pluginType",
                                "EVENT",
                                "pluginConfig",
                                plugin),
                        adminToken);
        assertEquals(201, created.statusCode(), created.body());
        long taskId = data(created).path("taskId").asLong();
        var scriptCreated =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "script-context",
                                "pluginType",
                                "SCRIPT",
                                "pluginConfig",
                                "{\"engine\":\"SHELL\",\"script\":\"echo"
                                    + " HIDDEN_SCRIPT\",\"workingDir\":\"/worker-only-directory\",\"env\":{\"TOKEN\":\"HIDDEN_ENV\"}}"),
                        adminToken);
        assertEquals(201, scriptCreated.statusCode(), scriptCreated.body());
        long scriptId = data(scriptCreated).path("taskId").asLong();
        for (String token : new String[] {operatorToken, adminToken}) {
            var safeContext =
                    send("GET", "/api/tasks/" + scriptId + "/execution-context", null, token);
            assertEquals(200, safeContext.statusCode());
            assertEquals("SHELL", data(safeContext).path("engine").asText(), safeContext.body());
            assertEquals("LOCAL", data(safeContext).path("executionMode").asText());
            assertFalse(safeContext.body().contains("HIDDEN_SCRIPT"));
            assertFalse(safeContext.body().contains("HIDDEN_ENV"));
            assertFalse(safeContext.body().contains("worker-only-directory"));
        }
        assertEquals(403, send("GET", "/api/tasks/" + scriptId, null, operatorToken).statusCode());
        assertEquals(
                401,
                send("GET", "/api/tasks/" + scriptId + "/execution-context", null, null)
                        .statusCode());
        var request =
                Map.of(
                        "requestId",
                        "audit-manual-replay",
                        "variables",
                        Map.of("secret", "HIDDEN_VARIABLE"));
        var first =
                send("POST", "/api/tasks/" + taskId + "/manual-trigger", request, operatorToken);
        var replay =
                send("POST", "/api/tasks/" + taskId + "/manual-trigger", request, operatorToken);
        assertEquals(202, first.statusCode(), first.body());
        assertEquals(202, replay.statusCode(), replay.body());
        assertEquals(data(first), data(replay));
        String triggerId = data(first).asText();
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                + " action_code='TASK_EXECUTION_TRIGGER' AND request_id=? AND"
                                + " result_code='ACCEPTED'",
                        Integer.class,
                        triggerId));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                + " action_code='TASK_EXECUTION_TRIGGER' AND request_id=? AND"
                                + " result_code='REPLAYED'",
                        Integer.class,
                        triggerId));
        var event =
                Map.of(
                        "sourceKey",
                        "security-http",
                        "eventCode",
                        "SECURITY_HTTP_EVENT",
                        "idempotencyKey",
                        "audit-event-replay",
                        "payload",
                        Map.of("secret", "HIDDEN_PAYLOAD"));
        assertEquals(403, send("POST", "/api/events", event, operatorToken).statusCode());
        var received = send("POST", "/api/events", event, adminToken);
        var repeated = send("POST", "/api/events", event, adminToken);
        assertEquals(200, received.statusCode(), received.body());
        assertEquals(200, repeated.statusCode(), repeated.body());
        assertFalse(data(received).path("duplicate").asBoolean());
        assertTrue(data(repeated).path("duplicate").asBoolean());
        assertEquals(data(received).path("eventId"), data(repeated).path("eventId"));
        var audits = send("GET", "/api/audits?pageSize=500", null, adminToken);
        assertFalse(audits.body().contains("HIDDEN_PAYLOAD"));
        assertFalse(audits.body().contains("HIDDEN_VARIABLE"));
        jdbc.execute(
                "CREATE TRIGGER test_task_audit_failure BEFORE INSERT ON ifj_operation_audit WHEN"
                    + " NEW.action_code='TASK_CREATE' AND NEW.result_code='SUCCESS' BEGIN SELECT"
                    + " RAISE(ABORT,'injected audit outage'); END");
        try {
            assertEquals(
                    500,
                    send(
                                    "POST",
                                    "/api/tasks",
                                    Map.of(
                                            "name",
                                            "rollback-task",
                                            "pluginType",
                                            "EVENT",
                                            "pluginConfig",
                                            plugin),
                                    adminToken)
                            .statusCode());
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ifj_task WHERE name='rollback-task'",
                            Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ifj_task_definition WHERE name='rollback-task'",
                            Integer.class));
        } finally {
            jdbc.execute("DROP TRIGGER test_task_audit_failure");
        }
    }

    @Test
    @Order(9)
    void sqliteIdsInsideAuditTransactionsNeverWaitForAnIndependentWriterOrEscapeRollback() {
        var generator = context.getBean(com.ifielsc.ijob.infra.coordination.id.IdGenerator.class);
        var manager =
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        long before =
                jdbc.queryForObject(
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_instance'",
                        Long.class);
        var allocated = new java.util.HashSet<Long>();
        new org.springframework.transaction.support.TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            for (int i = 0; i < 150; i++)
                                assertTrue(
                                        allocated.add(
                                                generator.nextId(
                                                        com.ifielsc.ijob.infra.coordination.id
                                                                .IdNamespace.INSTANCE)));
                            status.setRollbackOnly();
                        });
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT next_value FROM ifj_id_segment WHERE"
                                + " segment_key='pk:ifj_instance'",
                        Long.class));
    }

    @Test
    @Order(5)
    void approvalInboxUsesSnapshotNamesExactIdsAndDatabasePagination() throws Exception {
        String operator =
                jdbc.queryForObject(
                        "SELECT login_id FROM ifj_auth_user WHERE username='operator-user'",
                        String.class);
        for (int index = 0; index < 3; index++) approvalFixture(index, operator);
        var page =
                send(
                        "GET",
                        "/api/event-waits?taskName=APPROVAL%25_&pageNum=1&pageSize=2",
                        null,
                        operatorToken);
        assertEquals(200, page.statusCode(), page.body());
        assertEquals(3, data(page).path("total").asInt());
        assertEquals(2, data(page).path("list").size());
        assertEquals("9007199254741102", data(page).path("list").get(0).path("id").asText());
        assertEquals("Approval%_ 2", data(page).path("list").get(0).path("taskName").asText());
        assertEquals(
                "Approval%_ 2", data(page).path("list").get(0).path("rootTargetName").asText());
        assertFalse(page.body().contains("allowedOperatorIds"));
        assertFalse(page.body().contains("pluginConfig"));
        var second =
                send(
                        "GET",
                        "/api/event-waits?taskName=APPROVAL%25_&pageNum=2&pageSize=2",
                        null,
                        operatorToken);
        assertEquals("9007199254741100", data(second).path("list").get(0).path("id").asText());
        assertEquals(
                0, data(send("GET", "/api/event-waits", null, adminToken)).path("total").asInt());
        assertEquals(
                403,
                send("GET", "/api/event-waits/9007199254741100", null, adminToken).statusCode());
        assertEquals(
                403,
                send(
                                "POST",
                                "/api/event-waits/9007199254741100/approve",
                                Map.of("operatorId", operator),
                                adminToken)
                        .statusCode());
        assertEquals(403, send("GET", "/api/event-waits", null, viewerToken).statusCode());
        var byInstance =
                send("GET", "/api/event-waits/by-instance/9007199254741000", null, operatorToken);
        assertEquals("9007199254741100", data(byInstance).path("id").asText());
        assertTrue(data(byInstance).path("taskDefinitionId").isTextual());
        assertEquals(
                400,
                send("GET", "/api/event-waits?taskName=" + "x".repeat(129), null, operatorToken)
                        .statusCode());
        assertEquals(
                404,
                send("GET", "/api/event-waits/9223372036854775807", null, operatorToken)
                        .statusCode());
    }

    @Test
    @Order(6)
    void approvalDecisionsAreConditionalAuditedAndCompleteSuspendedRootTasks() throws Exception {
        String[] actions = {"approve", "reject", "withdraw"};
        String[] states = {"APPROVED", "REJECTED", "WITHDRAWN"};
        String[] instanceStates = {"SUCCESS", "FAILED", "STOPPED"};
        for (int index = 0; index < actions.length; index++) {
            String wait = Long.toString(9007199254741100L + index);
            String path = "/api/event-waits/" + wait + "/" + actions[index];
            assertEquals(
                    400,
                    send("POST", path, Map.of("comment", "x".repeat(501)), operatorToken)
                            .statusCode());
            String comment = index == 1 ? "x".repeat(500) : "reviewed";
            var result = send("POST", path, Map.of("comment", comment), operatorToken);
            assertEquals(200, result.statusCode(), result.body());
            assertEquals(states[index], data(result).path("status").asText());
            assertEquals("Approval%_ " + index, data(result).path("taskName").asText());
            assertEquals(comment, data(result).path("decisionComment").asText());
            var repeated =
                    send("POST", path, Map.of("comment", "must not overwrite"), operatorToken);
            assertEquals(200, repeated.statusCode(), repeated.body());
            assertEquals(comment, data(repeated).path("decisionComment").asText());
            String different = index == 0 ? "reject" : "approve";
            assertEquals(
                    409,
                    send(
                                    "POST",
                                    "/api/event-waits/" + wait + "/" + different,
                                    Map.of(),
                                    operatorToken)
                            .statusCode());
            assertEquals(
                    instanceStates[index],
                    jdbc.queryForObject(
                            "SELECT status FROM ifj_instance WHERE id=?",
                            String.class,
                            9007199254741000L + index));
        }
        assertEquals(
                0,
                data(send("GET", "/api/event-waits", null, operatorToken)).path("total").asInt());
        assertEquals(
                1,
                data(send("GET", "/api/event-waits?status=APPROVED", null, operatorToken))
                        .path("total")
                        .asInt());
        assertTrue(
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM ifj_operation_audit WHERE"
                                    + " action_code='EVENT_WAIT_APPROVE' AND result_code='SUCCESS'",
                                Integer.class)
                        > 0);
        String operator =
                jdbc.queryForObject(
                        "SELECT login_id FROM ifj_auth_user WHERE username='operator-user'",
                        String.class);
        var resolver =
                context.getBean(com.ifielsc.ijob.core.event.EventWaitResolutionService.class);
        var waits = context.getBean(com.ifielsc.ijob.infra.mapper.runtime.EventWaitMapper.class);
        approvalFixture(3, operator);
        assertTrue(
                resolver.timeout(
                        waits.selectById(9007199254741103L), java.time.LocalDateTime.now()));
        assertEquals(
                409,
                send("POST", "/api/event-waits/9007199254741103/approve", Map.of(), operatorToken)
                        .statusCode());
        assertEquals(
                "TIMED_OUT",
                data(send("GET", "/api/event-waits/9007199254741103", null, operatorToken))
                        .path("status")
                        .asText());
        approvalFixture(4, operator);
        var queue =
                context.getBean(
                        com.ifielsc.ijob.infra.coordination.work.DatabaseTriggerWorkQueue.class);
        var fence =
                context.getBean(
                        com.ifielsc.ijob.infra.coordination.work.ExecutionFenceCoordinator.class);
        long stopTrigger =
                jdbc.queryForObject(
                        "SELECT trigger_id FROM ifj_instance WHERE id=?",
                        Long.class,
                        9007199254741004L);
        var stopWork = queue.get(stopTrigger);
        fence.withSuspendedAccess(
                new com.ifielsc.ijob.infra.coordination.work.SuspendedWorkPermit(
                        stopTrigger, stopWork.getFenceToken()),
                () -> {
                    resolver.stopInstance(9007199254741004L);
                    return true;
                });
        assertEquals(
                409,
                send("POST", "/api/event-waits/9007199254741104/approve", Map.of(), operatorToken)
                        .statusCode());
        assertEquals(
                "STOPPED",
                data(send("GET", "/api/event-waits/9007199254741104", null, operatorToken))
                        .path("status")
                        .asText());
        approvalFixture(5, operator);
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET status='READY' WHERE root_instance_id=?",
                9007199254741005L);
        assertEquals(
                409,
                send("POST", "/api/event-waits/9007199254741105/approve", Map.of(), operatorToken)
                        .statusCode());
        assertEquals("WAITING", waits.selectById(9007199254741105L).getStatus().name());
        approvalFixture(6, operator);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var approve =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                        "POST",
                                        "/api/event-waits/9007199254741106/approve",
                                        Map.of("comment", "first"),
                                        operatorToken);
                            });
            var reject =
                    executor.submit(
                            () -> {
                                start.await();
                                return send(
                                        "POST",
                                        "/api/event-waits/9007199254741106/reject",
                                        Map.of("comment", "second"),
                                        operatorToken);
                            });
            start.countDown();
            var first = approve.get(10, java.util.concurrent.TimeUnit.SECONDS);
            var second = reject.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(
                    java.util.Set.of(200, 409),
                    java.util.Set.of(first.statusCode(), second.statusCode()),
                    first.body() + second.body());
            var winner = data(first.statusCode() == 200 ? first : second);
            var actual =
                    data(send("GET", "/api/event-waits/9007199254741106", null, operatorToken));
            assertEquals(winner.path("status"), actual.path("status"));
            assertEquals(winner.path("decisionComment"), actual.path("decisionComment"));
        }
        approvalFixture(7, operator);
        jdbc.update("DELETE FROM ifj_event_wait_operator WHERE wait_id=?", 9007199254741107L);
        assertEquals(
                500,
                send("GET", "/api/event-waits/9007199254741107", null, operatorToken).statusCode());
        assertEquals(
                500,
                send("POST", "/api/event-waits/9007199254741107/approve", Map.of(), operatorToken)
                        .statusCode());
        assertEquals("WAITING", waits.selectById(9007199254741107L).getStatus().name());
    }

    @Test
    @Order(6)
    void runGraphIsSafeForViewerAndUsesExecutionDefinition() throws Exception {
        long task = jdbc.queryForObject("SELECT MIN(id) FROM ifj_task", Long.class);
        var node =
                Map.of(
                        "nodeId",
                        "first",
                        "name",
                        "Snapshot first",
                        "kind",
                        "EXECUTION",
                        "nodeType",
                        "TASK",
                        "refId",
                        task,
                        "policy",
                        Map.of("failStrategy", "STOP", "executeCondition", "ALL_SUCCESS"));
        var second =
                Map.of(
                        "nodeId",
                        "later",
                        "name",
                        "Snapshot later",
                        "kind",
                        "EXECUTION",
                        "nodeType",
                        "TASK",
                        "refId",
                        task,
                        "policy",
                        Map.of("failStrategy", "STOP", "executeCondition", "ALL_SUCCESS"));
        var created =
                send(
                        "POST",
                        "/api/jobs",
                        Map.of(
                                "name",
                                "Runtime snapshot graph",
                                "enabled",
                                true,
                                "nodes",
                                java.util.List.of(node, second),
                                "edges",
                                java.util.List.of(
                                        Map.of("fromNodeId", "first", "toNodeId", "later"))),
                        adminToken);
        assertEquals(201, created.statusCode(), created.body());
        long job = data(created).path("jobId").asLong(),
                definition = data(created).path("definitionId").asLong();
        long root = 9007199254750000L, child = root + 1;
        long first =
                jdbc.queryForObject(
                        "SELECT id FROM ifj_job_definition_node WHERE definition_id=? AND"
                                + " node_key='first'",
                        Long.class,
                        definition);
        jdbc.update(
                "INSERT INTO"
                    + " ifj_instance(id,root_instance_id,target_type,target_id,status,definition_id)"
                    + " VALUES (?,?,'JOB',?,'RUNNING',?)",
                root,
                root,
                job,
                definition);
        jdbc.update(
                "INSERT INTO"
                    + " ifj_instance(id,root_instance_id,parent_instance_id,target_type,target_id,status,definition_node_id,input_variables)"
                    + " VALUES (?,?,?,'TASK',?,'SUCCESS',?,'secret-graph-input')",
                child,
                root,
                root,
                task,
                first);
        jdbc.update("UPDATE ifj_job SET name='Changed current name' WHERE id=?", job);
        var response = send("GET", "/api/instances/" + root + "/graph", null, viewerToken);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("Runtime snapshot graph", data(response).path("name").asText());
        assertEquals(Long.toString(root), data(response).path("instanceId").asText());
        assertTrue(data(response).path("instanceId").isTextual());
        assertEquals(2, data(response).path("nodes").size());
        assertEquals(1, data(response).path("edges").size());
        var statuses = new java.util.HashSet<String>();
        data(response).path("nodes").forEach(n -> statuses.add(n.path("status").asText()));
        assertEquals(java.util.Set.of("SUCCESS", "NOT_STARTED"), statuses);
        assertFalse(response.body().contains("secret-graph-input"));
        assertFalse(response.body().contains("pluginConfig"));
        assertFalse(response.body().contains("conditionPayload"));
        assertEquals(
                409,
                send("GET", "/api/instances/" + child + "/graph", null, viewerToken).statusCode());
        assertEquals(
                404,
                send("GET", "/api/instances/9223372036854775807/graph", null, viewerToken)
                        .statusCode());
    }

    @Test
    @Order(6)
    void eventConfigurationAndBindingContracts() throws Exception {
        var account =
                send(
                        "POST",
                        "/api/users",
                        Map.of(
                                "username",
                                "event-approver",
                                "displayName",
                                "事件%_人员",
                                "password",
                                INITIAL,
                                "roleCode",
                                "OPERATOR"),
                        adminToken);
        assertEquals(200, account.statusCode(), account.body());
        String loginId = data(account).path("loginId").asText();
        String userId = data(account).path("id").asText();
        var search = send("GET", "/api/users/approvers?pageSize=1&name=%25_", null, adminToken);
        assertEquals(200, search.statusCode(), search.body());
        assertEquals(1, data(search).size());
        assertEquals(loginId, data(search).get(0).path("loginId").asText());
        assertEquals(
                0,
                data(send(
                                "GET",
                                "/api/users/approvers?pageNum=2&pageSize=1&name=%25_",
                                null,
                                adminToken))
                        .size());
        assertEquals(
                400,
                send("GET", "/api/users/approvers?pageSize=501", null, adminToken).statusCode());
        for (String token : new String[] {viewerToken, operatorToken}) {
            assertEquals(403, send("GET", "/api/users/approvers", null, token).statusCode());
            assertEquals(
                    403,
                    send("POST", "/api/users/approvers/resolve", java.util.List.of(loginId), token)
                            .statusCode());
        }
        var resolved =
                send(
                        "POST",
                        "/api/users/approvers/resolve",
                        java.util.List.of(loginId, "absent", userId),
                        adminToken);
        assertEquals("AVAILABLE", data(resolved).get(0).path("status").asText());
        assertEquals("MISSING", data(resolved).get(1).path("status").asText());
        assertEquals("MISSING", data(resolved).get(2).path("status").asText());
        assertFalse(resolved.body().contains("password"));
        assertFalse(resolved.body().contains("securityVersion"));
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/users/approvers/resolve",
                                java.util.Collections.nCopies(101, loginId),
                                adminToken)
                        .statusCode());
        String approval =
                json.writeValueAsString(
                        Map.of(
                                "action",
                                "WAIT_APPROVAL",
                                "allowedOperatorIds",
                                java.util.List.of(loginId)));
        var valid =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "EVENT publish validation",
                                "pluginType",
                                "EVENT",
                                "pluginConfig",
                                approval),
                        adminToken);
        assertEquals(201, valid.statusCode(), valid.body());
        String taskId = data(valid).path("taskId").asText();
        jdbc.update("UPDATE ifj_auth_user SET enabled=0 WHERE login_id=?", loginId);
        var invalid =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "Invalid approval",
                                "pluginType",
                                "EVENT",
                                "pluginConfig",
                                approval),
                        adminToken);
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertEquals(
                "error.security.approver-invalid",
                json.readTree(invalid.body()).path("messageKey").asText());
        assertEquals(
                "DISABLED",
                data(send(
                                "POST",
                                "/api/users/approvers/resolve",
                                java.util.List.of(loginId),
                                adminToken))
                        .get(0)
                        .path("status")
                        .asText());
        jdbc.update(
                "UPDATE ifj_auth_user SET enabled=1, role_code='VIEWER' WHERE login_id=?", loginId);
        assertEquals(
                "FORBIDDEN",
                data(send(
                                "POST",
                                "/api/users/approvers/resolve",
                                java.util.List.of(loginId),
                                adminToken))
                        .get(0)
                        .path("status")
                        .asText());
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "Invalid role",
                                        "pluginType",
                                        "EVENT",
                                        "pluginConfig",
                                        approval),
                                adminToken)
                        .statusCode());
        jdbc.update("DELETE FROM ifj_auth_user WHERE login_id=?", loginId);
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "Deleted approver",
                                        "pluginType",
                                        "EVENT",
                                        "pluginConfig",
                                        approval),
                                adminToken)
                        .statusCode());
        String huge =
                json.writeValueAsString(
                        Map.of(
                                "action",
                                "PUBLISH",
                                "eventCode",
                                "report.done",
                                "payload",
                                "中".repeat(5461)));
        assertEquals(
                400,
                send(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "Large payload",
                                        "pluginType",
                                        "EVENT",
                                        "pluginConfig",
                                        huge),
                                adminToken)
                        .statusCode());
        String good =
                json.writeValueAsString(
                        Map.of("action", "PUBLISH", "eventCode", "report.done", "payload", false));
        assertEquals(
                201,
                send(
                                "POST",
                                "/api/tasks",
                                Map.of(
                                        "name",
                                        "JSON false",
                                        "pluginType",
                                        "EVENT",
                                        "pluginConfig",
                                        good),
                                adminToken)
                        .statusCode());
        var created =
                send(
                        "POST",
                        "/api/event-bindings",
                        Map.of(
                                "targetType",
                                "TASK",
                                "targetId",
                                taskId,
                                "eventCode",
                                "report.ready"),
                        adminToken);
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(data(created).isTextual());
        String oldId = data(created).asText();
        jdbc.update(
                "UPDATE ifj_event_binding SET id=9223372036854775807 WHERE id=?",
                Long.parseLong(oldId));
        String bindingId = "9223372036854775807";
        var binding = send("GET", "/api/event-bindings/" + bindingId, null, adminToken);
        assertTrue(data(binding).path("id").isTextual());
        assertEquals(bindingId, data(binding).path("id").asText());
        assertTrue(data(binding).path("targetId").isTextual());
        assertEquals(
                409,
                send(
                                "POST",
                                "/api/event-bindings",
                                Map.of(
                                        "targetType",
                                        "TASK",
                                        "targetId",
                                        taskId,
                                        "eventCode",
                                        "report.ready"),
                                adminToken)
                        .statusCode());
        assertEquals(
                403,
                send(
                                "GET",
                                "/api/event-bindings?targetType=TASK&targetId=" + taskId,
                                null,
                                operatorToken)
                        .statusCode());
        assertEquals(
                403,
                send(
                                "PUT",
                                "/api/event-bindings/" + bindingId,
                                Map.of("enabled", false),
                                operatorToken)
                        .statusCode());
        assertEquals(
                200,
                send(
                                "PUT",
                                "/api/event-bindings/" + bindingId,
                                Map.of("enabled", false),
                                adminToken)
                        .statusCode());
        assertEquals(
                200,
                send("DELETE", "/api/event-bindings/" + bindingId, null, adminToken).statusCode());
        assertEquals(
                404,
                send("GET", "/api/event-bindings/" + bindingId, null, adminToken).statusCode());
    }

    private void approvalFixture(int index, String operator) throws Exception {
        String config =
                json.writeValueAsString(
                        Map.of(
                                "action",
                                "WAIT_APPROVAL",
                                "allowedOperatorIds",
                                java.util.List.of(operator)));
        var created =
                send(
                        "POST",
                        "/api/tasks",
                        Map.of(
                                "name",
                                "Approval%_ " + index,
                                "pluginType",
                                "EVENT",
                                "pluginConfig",
                                config,
                                "enabled",
                                true),
                        adminToken);
        assertEquals(201, created.statusCode(), created.body());
        long task = data(created).path("taskId").asLong();
        long definition = data(created).path("definitionId").asLong();
        var triggered =
                send(
                        "POST",
                        "/api/tasks/" + task + "/manual-trigger",
                        Map.of("requestId", "approval-fixture-" + index),
                        operatorToken);
        assertEquals(202, triggered.statusCode(), triggered.body());
        long trigger = data(triggered).asLong(),
                instance = 9007199254741000L + index,
                wait = 9007199254741100L + index;
        jdbc.update(
                "INSERT INTO"
                    + " ifj_instance(id,root_instance_id,target_type,target_id,trigger_type,trigger_id,status,task_definition_id)"
                    + " VALUES (?,?,'TASK',?,'MANUAL',?,'WAITING_EVENT',?)",
                instance,
                instance,
                task,
                trigger,
                definition);
        jdbc.update("UPDATE ifj_trigger SET status='RUNNING' WHERE id=?", trigger);
        jdbc.update(
                "UPDATE ifj_trigger_work_item SET status='SUSPENDED',root_instance_id=? WHERE"
                        + " trigger_id=?",
                instance,
                trigger);
        jdbc.update(
                "INSERT INTO"
                    + " ifj_event_wait(id,instance_id,root_instance_id,attempt_no,wait_type,status,accept_after,allowed_operator_ids,create_time)"
                    + " VALUES (?,?,?,0,'APPROVAL','WAITING','2026-09-27"
                    + " 08:00:00.000',?,'2026-09-27 08:00:00.000')",
                wait,
                instance,
                instance,
                json.writeValueAsString(java.util.List.of(operator)));
        jdbc.update(
                "INSERT INTO ifj_event_wait_operator(wait_id,operator_id) VALUES (?,?)",
                wait,
                operator);
        jdbc.update("UPDATE ifj_task SET name='Current name changed' WHERE id=?", task);
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
