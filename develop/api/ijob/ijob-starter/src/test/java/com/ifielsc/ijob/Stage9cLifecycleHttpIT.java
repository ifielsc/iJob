// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

/** Real HTTP, scheduler and Worker acceptance; every target belongs to this temporary fixture. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Stage9cLifecycleHttpIT {
    @TempDir static Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger notifications = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maximum = new AtomicInteger();
    private volatile CountDownLatch release = new CountDownLatch(0);
    private ServletWebServerApplicationContext context;
    private HttpServer target;
    private ServerSocket smtp;
    private ExecutorService smtpThread;
    private ExecutorService targetThreads;
    private JdbcTemplate jdbc;
    private String base;
    private String token;
    private String serviceId;
    private String targetUrl;

    @BeforeAll
    void start() throws Exception {
        smtp = new ServerSocket();
        smtp.bind(new InetSocketAddress("127.0.0.1", 0));
        smtpThread = Executors.newSingleThreadExecutor();
        smtpThread.submit(
                () -> {
                    while (!smtp.isClosed()) {
                        try (Socket socket = smtp.accept();
                                BufferedReader input =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        socket.getInputStream(),
                                                        StandardCharsets.UTF_8));
                                PrintWriter output =
                                        new PrintWriter(
                                                new OutputStreamWriter(
                                                        socket.getOutputStream(),
                                                        StandardCharsets.UTF_8),
                                                true)) {
                            socket.setSoTimeout(10000);
                            output.print("220 localhost acceptance\r\n");
                            output.flush();
                            boolean data = false;
                            String line;
                            while ((line = input.readLine()) != null) {
                                if (data) {
                                    if (!line.equals(".")) continue;
                                    notifications.incrementAndGet();
                                    data = false;
                                } else if (line.equals("DATA")) {
                                    output.print("354 End with dot\r\n");
                                    output.flush();
                                    data = true;
                                    continue;
                                } else if (line.equals("QUIT")) {
                                    output.print("221 Bye\r\n");
                                    output.flush();
                                    break;
                                }
                                output.print("250 OK\r\n");
                                output.flush();
                            }
                        } catch (IOException error) {
                            if (!smtp.isClosed())
                                throw new IllegalStateException("Isolated SMTP failed", error);
                        }
                    }
                });
        target = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        targetThreads = Executors.newFixedThreadPool(4);
        target.setExecutor(targetThreads);
        target.createContext(
                "/",
                exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    exchange.getRequestBody().readAllBytes();
                    if (path.equals("/notify")) notifications.incrementAndGet();
                    else calls.incrementAndGet();
                    if (path.equals("/hold")) {
                        int running = active.incrementAndGet();
                        maximum.accumulateAndGet(running, Math::max);
                        try {
                            release.await(25, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        } finally {
                            active.decrementAndGet();
                        }
                    }
                    byte[] body = "{\"errcode\":0,\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        target.start();
        targetUrl = "http://127.0.0.1:" + target.getAddress().getPort();
        String password = "Stage9c-" + UUID.randomUUID();
        context =
                (ServletWebServerApplicationContext)
                        new SpringApplicationBuilder(IJobApplication.class)
                                .registerShutdownHook(false)
                                .run(
                                        "--server.address=127.0.0.1",
                                        "--server.port=0",
                                        "--spring.config.location=classpath:/application.yml",
                                        "--spring.config.import=optional:classpath:database/sqlite.yml",
                                        "--spring.datasource.url=jdbc:sqlite:"
                                                + directory.resolve("lifecycle.db").toAbsolutePath()
                                                + "?foreign_keys=on&busy_timeout=10000&journal_mode=WAL&transaction_mode=IMMEDIATE",
                                        "--spring.datasource.hikari.minimum-idle=0",
                                        "--spring.flyway.enabled=true",
                                        "--spring.flyway.locations=classpath:db/release/sqlite",
                                        "--app.platform-database.vendor=SQLITE",
                                        "--app.runtime.mode=STANDALONE",
                                        "--app.runtime.instance-name=stage9c-isolated",
                                        "--app.runtime.roles=API,WORKER,SCHEDULER",
                                        "--app.auth.mode=DATABASE",
                                        "--app.auth.bootstrap-enabled=true",
                                        "--app.auth.username=stage9c-admin",
                                        "--app.auth.password-hash="
                                                + new BCryptPasswordEncoder(4).encode(password),
                                        "--app.security.aes.key-id=stage9c",
                                        "--app.security.aes.key="
                                                + java.util.Base64.getEncoder()
                                                        .encodeToString(new byte[32]),
                                        "--app.schedule.enabled=true",
                                        "--app.schedule.initial-delay=1s",
                                        "--app.schedule.scan-interval=200ms",
                                        "--app.event.recovery.enabled=true",
                                        "--app.event.ingress.enabled=true",
                                        "--app.notification.outbox.enabled=true",
                                        "--app.notification.outbox.scan-interval=200ms",
                                        "--app.notify.email.enabled=true",
                                        "--app.retention.enabled=false",
                                        "--app.plugin.local-jar.enabled=false",
                                        "--spring.jmx.enabled=false",
                                        "--spring.main.banner-mode=off",
                                        "--logging.level.root=OFF");
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
        jdbc.update("UPDATE ifj_auth_user SET must_change_password=0");
        token =
                call(
                                "POST",
                                "/api/auth/login",
                                Map.of("username", "stage9c-admin", "password", password),
                                200)
                        .path("tokenValue")
                        .asText();
        serviceId =
                call(
                                "POST",
                                "/api/resources",
                                Map.of(
                                        "name",
                                        "Loopback service",
                                        "code",
                                        "stage9c-loopback",
                                        "type",
                                        "SERVICE",
                                        "enabled",
                                        true,
                                        "serviceConfig",
                                        Map.of(
                                                "protocol",
                                                "HTTP",
                                                "endpoint",
                                                targetUrl,
                                                "authType",
                                                "NONE",
                                                "callTimeout",
                                                30)),
                                201)
                        .asText();
    }

    @AfterAll
    void stop() throws IOException {
        release.countDown();
        if (context != null) context.close();
        if (target != null) target.stop(0);
        if (targetThreads != null) targetThreads.shutdownNow();
        if (smtp != null) smtp.close();
        if (smtpThread != null) smtpThread.shutdownNow();
    }

    @Test
    void calendarScheduleExecutesPublishedJobAndDeliversAuditedNotification() throws Exception {
        JsonNode task =
                task(
                        "Scheduled task",
                        "API_CALL",
                        Map.of("resourceId", serviceId, "method", "GET", "path", "/scheduled"));
        String taskId = task.path("taskId").asText();
        String jobId =
                call(
                                "POST",
                                "/api/jobs",
                                Map.of(
                                        "name",
                                        "Scheduled job",
                                        "enabled",
                                        true,
                                        "nodes",
                                        List.of(
                                                Map.of(
                                                        "nodeId",
                                                        "first",
                                                        "name",
                                                        "First",
                                                        "kind",
                                                        "EXECUTION",
                                                        "nodeType",
                                                        "TASK",
                                                        "refId",
                                                        taskId,
                                                        "policy",
                                                        Map.of(
                                                                "failStrategy",
                                                                "STOP",
                                                                "executeCondition",
                                                                "ALL_SUCCESS"))),
                                        "edges",
                                        List.of()),
                                201)
                        .path("jobId")
                        .asText();
        String mailChannelId =
                call(
                                "POST",
                                "/api/mail-channels",
                                Map.of(
                                        "name",
                                        "Loopback SMTP",
                                        "host",
                                        "127.0.0.1",
                                        "port",
                                        smtp.getLocalPort(),
                                        "security",
                                        "NONE",
                                        "authentication",
                                        false,
                                        "from",
                                        "acceptance@example.test",
                                        "enabled",
                                        true),
                                201)
                        .asText();
        String notifyId =
                call(
                                "POST",
                                "/api/notify-configs",
                                Map.of(
                                        "name",
                                        "Loopback notification",
                                        "type",
                                        "EMAIL",
                                        "enabled",
                                        true,
                                        "emailConfig",
                                        Map.of(
                                                "mailChannelId", mailChannelId,
                                                "to", List.of("sink@example.test"),
                                                "cc", List.of())),
                                201)
                        .asText();
        call(
                "POST",
                "/api/notify-bindings",
                Map.of(
                        "targetType",
                        "JOB",
                        "targetId",
                        jobId,
                        "notifyConfigId",
                        notifyId,
                        "notifyPolicy",
                        "ALWAYS"),
                201);
        String calendarId =
                call(
                                "POST",
                                "/api/calendars",
                                Map.of(
                                        "name",
                                        "Every day",
                                        "workdays",
                                        List.of(
                                                "MONDAY",
                                                "TUESDAY",
                                                "WEDNESDAY",
                                                "THURSDAY",
                                                "FRIDAY",
                                                "SATURDAY",
                                                "SUNDAY"),
                                        "exceptions",
                                        List.of()),
                                201)
                        .asText();
        LocalDateTime fire =
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(3).withNano(0);
        String scheduleId =
                call(
                                "POST",
                                "/api/schedules",
                                Map.of(
                                        "targetType",
                                        "JOB",
                                        "targetId",
                                        jobId,
                                        "scheduleType",
                                        "ONCE",
                                        "startTime",
                                        fire.toString(),
                                        "calendarId",
                                        calendarId,
                                        "enabled",
                                        true),
                                201)
                        .asText();
        await(
                () ->
                        get("/api/triggers?targetType=JOB&targetId=" + jobId).path("list").size()
                                == 1,
                "scheduled trigger");
        String triggerId =
                get("/api/triggers?targetType=JOB&targetId=" + jobId)
                        .path("list")
                        .get(0)
                        .path("id")
                        .asText();
        await(() -> status(triggerId).equals("SUCCESS"), "scheduled job success");
        await(() -> notifications.get() > 0, "notification delivered to loopback");
        await(
                () -> get("/api/notify-send-logs").path("list").toString().contains(notifyId),
                "notification send audit");
        JsonNode trigger = get("/api/triggers/" + triggerId);
        assertEquals(scheduleId, trigger.path("scheduleId").asText());
        JsonNode graph = get("/api/instances/" + trigger.path("rootInstanceId").asText() + "/tree");
        assertEquals(2, graph.path("nodes").size());
        assertTrue(get("/api/audits?action=SCHEDULE_CREATE").path("total").asInt() > 0);
        assertEquals(
                1, get("/api/triggers?targetType=JOB&targetId=" + jobId).path("total").asInt());
        assertFalse(
                get("/api/workbench").path("node").path("workerRoleConfigured").isMissingNode());
    }

    @Test
    void workerApprovalAndEventReceiptResumeRealWaitingTasks() throws Exception {
        String loginId =
                jdbc.queryForObject(
                        "SELECT login_id FROM ifj_auth_user WHERE username='stage9c-admin'",
                        String.class);
        String approvalTask =
                task(
                                "Approval",
                                "EVENT",
                                Map.of(
                                        "action",
                                        "WAIT_APPROVAL",
                                        "allowedOperatorIds",
                                        List.of(loginId),
                                        "timeoutSeconds",
                                        60))
                        .path("taskId")
                        .asText();
        String approvalTrigger = trigger(approvalTask);
        await(
                () ->
                        get("/api/event-waits?status=WAITING&taskId=" + approvalTask)
                                        .path("list")
                                        .size()
                                == 1,
                "approval waiting");
        String waitId =
                get("/api/event-waits?status=WAITING&taskId=" + approvalTask)
                        .path("list")
                        .get(0)
                        .path("id")
                        .asText();
        call(
                "POST",
                "/api/event-waits/" + waitId + "/approve",
                Map.of("comment", "Isolated acceptance"),
                200);
        await(() -> status(approvalTrigger).equals("SUCCESS"), "approval continuation");
        call("POST", "/api/event-waits/" + waitId + "/reject", Map.of(), 409);
        String eventTask =
                task(
                                "Wait event",
                                "EVENT",
                                Map.of(
                                        "action",
                                        "WAIT_EVENT",
                                        "eventCode",
                                        "stage9c.receipt",
                                        "businessKey",
                                        "acceptance",
                                        "timeoutSeconds",
                                        60))
                        .path("taskId")
                        .asText();
        String eventTrigger = trigger(eventTask);
        await(
                () ->
                        jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM ifj_event_wait WHERE status='WAITING'"
                                                + " AND event_code='stage9c.receipt'",
                                        Integer.class)
                                == 1,
                "event wait registered");
        Map<String, Object> receipt =
                Map.of(
                        "sourceKey",
                        "loopback",
                        "eventCode",
                        "stage9c.receipt",
                        "businessKey",
                        "acceptance",
                        "idempotencyKey",
                        "stage9c-once",
                        "payload",
                        Map.of("value", "accepted"));
        assertFalse(call("POST", "/api/events", receipt, 200).path("duplicate").asBoolean());
        assertTrue(call("POST", "/api/events", receipt, 200).path("duplicate").asBoolean());
        await(() -> status(eventTrigger).equals("SUCCESS"), "event continuation");
    }

    @Test
    void sharedConcurrencyPermitSerializesTwoRealExecutionsAndReleases() throws Exception {
        release = new CountDownLatch(1);
        maximum.set(0);
        String taskId =
                task(
                                "Serial task",
                                "API_CALL",
                                Map.of("resourceId", serviceId, "method", "GET", "path", "/hold"))
                        .path("taskId")
                        .asText();
        String resourceId =
                call(
                                "POST",
                                "/api/concurrency/resources",
                                Map.of(
                                        "code",
                                        "stage9c-serial",
                                        "name",
                                        "One slot",
                                        "maxConcurrency",
                                        1),
                                201)
                        .asText();
        call(
                "PUT",
                "/api/concurrency/tasks/" + taskId,
                Map.of(
                        "expectedVersion",
                        "0",
                        "taskMaxConcurrency",
                        1,
                        "resourceIds",
                        List.of(resourceId)),
                200);
        String first = trigger(taskId);
        await(() -> active.get() == 1, "first acquired slot");
        String second = trigger(taskId);
        await(() -> get("/api/triggers/" + second).hasNonNull("rootInstanceId"), "second admitted");
        assertEquals(1, maximum.get());
        call(
                "DELETE",
                "/api/concurrency/resources/" + resourceId + "?expectedVersion=1",
                null,
                409);
        release.countDown();
        await(
                () -> status(first).equals("SUCCESS") && status(second).equals("SUCCESS"),
                "both serialized runs completed");
        assertEquals(1, maximum.get());
        await(
                () ->
                        get("/api/concurrency/resources/" + resourceId)
                                        .path("activeInstanceCount")
                                        .asInt()
                                == 0,
                "permits released");
    }

    private JsonNode task(String name, String plugin, Map<String, Object> config) throws Exception {
        return call(
                "POST",
                "/api/tasks",
                Map.of(
                        "name",
                        name,
                        "enabled",
                        true,
                        "pluginType",
                        plugin,
                        "pluginConfig",
                        json.writeValueAsString(config)),
                201);
    }

    @Test
    void mailChannelDeletionUsesOneTransactionAndPreservesReferencedChannels() throws Exception {
        Map<String, Object> settings =
                Map.of(
                        "name",
                        "Deletion acceptance",
                        "host",
                        "127.0.0.1",
                        "port",
                        smtp.getLocalPort(),
                        "security",
                        "NONE",
                        "authentication",
                        false,
                        "from",
                        "delete@example.test");
        String first = call("POST", "/api/mail-channels", settings, 201).asText();
        String second = call("POST", "/api/mail-channels", settings, 201).asText();
        String config =
                call(
                                "POST",
                                "/api/notify-configs",
                                Map.of(
                                        "name",
                                        "Disabled reference",
                                        "type",
                                        "EMAIL",
                                        "enabled",
                                        false,
                                        "emailConfig",
                                        Map.of(
                                                "mailChannelId",
                                                second,
                                                "to",
                                                List.of("local@example.test"))),
                                201)
                        .asText();
        call("DELETE", "/api/mail-channels/batch", Map.of("ids", List.of(first, second)), 409);
        assertEquals(first, get("/api/mail-channels/" + first).path("id").asText());
        assertEquals(second, get("/api/mail-channels/" + second).path("id").asText());
        call("DELETE", "/api/notify-configs/" + config, null, 200);

        // Fail the second row at the database boundary: the first deletion must roll back too.
        jdbc.execute(
                "CREATE TRIGGER reject_mail_delete BEFORE DELETE ON ifj_mail_channel "
                        + "WHEN OLD.id = "
                        + second
                        + " BEGIN SELECT RAISE(ABORT, 'isolated rollback check'); END");
        try {
            call("DELETE", "/api/mail-channels/batch", Map.of("ids", List.of(first, second)), 500);
            assertEquals(first, get("/api/mail-channels/" + first).path("id").asText());
            assertEquals(second, get("/api/mail-channels/" + second).path("id").asText());
        } finally {
            jdbc.execute("DROP TRIGGER reject_mail_delete");
        }
        call("DELETE", "/api/mail-channels/batch", Map.of("ids", List.of(first, second)), 200);
        call("GET", "/api/mail-channels/" + first, null, 404);
        call("GET", "/api/mail-channels/" + second, null, 404);
    }

    @Test
    void runningTaskCanPauseResumeAndStopWithIdempotentCommands() throws Exception {
        release = new CountDownLatch(1);
        String taskId =
                task(
                                "Controlled task",
                                "API_CALL",
                                Map.of("resourceId", serviceId, "method", "GET", "path", "/hold"))
                        .path("taskId")
                        .asText();
        String triggerId = trigger(taskId);
        await(() -> active.get() == 1, "controlled task entered target");
        String root = get("/api/triggers/" + triggerId).path("rootInstanceId").asText();
        for (String action : List.of("pause", "resume", "stop")) {
            String requestId = UUID.randomUUID().toString();
            JsonNode accepted = command(root, action, requestId);
            assertEquals(root, accepted.path("instanceId").asText());
            String expected =
                    action.equals("pause")
                            ? "PAUSED"
                            : action.equals("resume") ? "RUNNING" : "STOPPED";
            await(
                    () -> get("/api/instances/" + root).path("status").asText().equals(expected),
                    action + " terminal state");
            assertTrue(command(root, action, requestId).path("replayed").asBoolean());
            assertEquals(
                    requestId,
                    get("/api/runtime-commands/" + requestId).path("requestId").asText());
        }
        release.countDown();
    }

    private JsonNode command(String root, String action, String requestId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(base + "/api/instances/" + root + "/" + action))
                        .timeout(Duration.ofSeconds(15))
                        .header("ijob-token", token)
                        .header("Idempotency-Key", requestId)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "control " + action);
        return json.readTree(response.body()).path("data");
    }

    private String trigger(String taskId) throws Exception {
        return call(
                        "POST",
                        "/api/tasks/" + taskId + "/manual-trigger",
                        Map.of("requestId", UUID.randomUUID().toString()),
                        202)
                .asText();
    }

    private String status(String triggerId) {
        return get("/api/triggers/" + triggerId).path("status").asText();
    }

    private JsonNode get(String path) {
        try {
            return call("GET", path, null, 200);
        } catch (Exception error) {
            throw new IllegalStateException("Isolated HTTP read failed: " + path, error);
        }
    }

    private JsonNode call(String method, String path, Object body, int expected) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(base + path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json");
        if (token != null) request.header("ijob-token", token);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> response =
                client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(
                expected,
                response.statusCode(),
                method + " " + path + " code=" + json.readTree(response.body()).path("code"));
        return json.readTree(response.body()).path("data");
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(35).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        assertTrue(condition.getAsBoolean(), description);
    }
}
