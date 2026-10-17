package com.ifelio.ijob.core.resource.factory.service.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.core.resource.factory.service.ServiceRequest;
import com.ifelio.ijob.core.resource.factory.service.ServiceResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class HttpServiceClientTest {
    @Test
    void controlRetryAfterMustNotReplayRequest() throws Exception {
        var calls = new AtomicInteger();
        start(
                exchange -> {
                    calls.incrementAndGet();
                    exchange.getResponseHeaders().add("Retry-After", "0");
                    respond(exchange, 503, "unavailable");
                });
        var client = client(Map.of());
        var request = request("POST", "/stop", null);
        request.setControlRequest(true);
        try {
            client.invoke(request);
        } catch (BusinessException expected) {
            /* A prohibited replay may fail locally. */
        }
        assertEquals(1, calls.get());
    }

    @Test
    void expiredControlDoesNotAcquireTokenOrMarkBusinessDispatched() throws Exception {
        start(
                exchange -> {
                    throw new AssertionError("No request expected");
                });
        var provider =
                new OAuth2TokenProvider() {
                    public String getBearerToken() {
                        throw new AssertionError("No token expected");
                    }

                    public void invalidate() {}
                };
        var client = new HttpServiceClient(new OkHttpClient(), endpoint(), Map.of(), provider);
        clients.add(client);
        var request = request("POST", "/stop", null);
        request.setControlRequest(true);
        request.setDeadlineNanos(System.nanoTime() - 1);
        try (var call = client.newInvocation(request)) {
            assertThrows(
                    java.util.concurrent.CancellationException.class,
                    () ->
                            call.executeWithDispatch(
                                    () -> {
                                        throw new AssertionError("Not dispatched");
                                    }));
        }
    }

    @Test
    void controlResponseNeverFollowsRedirect() throws Exception {
        var calls = new AtomicInteger();
        start(
                exchange -> {
                    calls.incrementAndGet();
                    exchange.getResponseHeaders().add("Location", "/redirect");
                    respond(exchange, 307, "redirect");
                });
        var client = client(Map.of());
        var request = request("POST", "/stop", null);
        request.setControlRequest(true);
        assertEquals(307, client.invoke(request).getStatus());
        assertEquals(1, calls.get());
    }

    private final List<ExecutorService> executors = new ArrayList<>();
    private final List<HttpServiceClient> clients = new ArrayList<>();
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
        }
        clients.forEach(HttpServiceClient::close);
        executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void postShouldJoinPathMergeHeadersAndCaptureResponse() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        start(
                exchange -> {
                    recorded.set(record(exchange));
                    exchange.getResponseHeaders().add("X-Reply", "accepted");
                    respond(exchange, 201, "{\"accepted\":true}");
                });
        HttpServiceClient client =
                client(Map.of("Authorization", "resource-token", "X-Default", "resource"));
        ServiceRequest request =
                request(
                        "POST",
                        "/users",
                        Map.of(
                                "authorization", "task-token",
                                "Content-Type", "application/json"));
        request.setBody(Map.of("id", 7));

        ServiceResponse response = client.invoke(request);

        RecordedRequest actual = recorded.get();
        assertAll(
                () -> assertEquals("POST", actual.method()),
                () -> assertEquals("/base/users", actual.path()),
                () -> assertEquals("task-token", actual.authorization()),
                () -> assertEquals("resource", actual.defaultHeader()),
                () -> assertEquals("{\"id\":7}", actual.body()),
                () -> assertEquals(201, response.getStatus()),
                () -> assertEquals("{\"accepted\":true}", response.getBody()),
                () -> assertEquals("accepted", header(response, "X-Reply")),
                () -> assertNotNull(response.getElapsed()),
                () -> assertTrue(client.success(response)));
    }

    @Test
    void jsonScalarsAndLongIntegersShouldRetainTheirWireTypes() throws Exception {
        List<RecordedRequest> recorded = new ArrayList<>();
        start(
                exchange -> {
                    recorded.add(record(exchange));
                    respond(exchange, 200, "ok");
                });
        HttpServiceClient client = client(Map.of());
        for (Object body : List.of(false, 0, "text", List.of(), Map.of("id", Long.MAX_VALUE))) {
            ServiceRequest request = request("POST", "/scalar", null);
            request.setBody(body);
            client.invoke(request);
        }
        assertEquals(
                List.of("false", "0", "\"text\"", "[]", "{\"id\":9223372036854775807}"),
                recorded.stream().map(RecordedRequest::body).toList());
    }

    @Test
    void getAndHeadShouldNotCreateRequestBodies() throws Exception {
        List<RecordedRequest> requests = new ArrayList<>();
        start(
                exchange -> {
                    requests.add(record(exchange));
                    if ("HEAD".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        exchange.close();
                    } else {
                        respond(exchange, 200, "ok");
                    }
                });
        HttpServiceClient client = client(Map.of());

        ServiceResponse get = client.invoke(request("get", "items", null));
        ServiceResponse head = client.invoke(request("HEAD", "items", null));

        assertAll(
                () -> assertEquals(200, get.getStatus()),
                () -> assertEquals(204, head.getStatus()),
                () ->
                        assertEquals(
                                List.of("GET", "HEAD"),
                                requests.stream().map(RecordedRequest::method).toList()),
                () -> assertTrue(requests.stream().allMatch(request -> request.body().isEmpty())));
    }

    @Test
    void bodyOptionalMethodsShouldNotSynthesizeJsonBodies() throws Exception {
        List<RecordedRequest> requests = new ArrayList<>();
        start(
                exchange -> {
                    requests.add(record(exchange));
                    respond(exchange, 200, "ok");
                });
        HttpServiceClient client = client(Map.of());

        client.invoke(request("DELETE", "items/1", null));
        client.invoke(request("OPTIONS", "items", null));
        client.invoke(request("POST", "items", null));

        assertAll(
                () ->
                        assertEquals(
                                List.of("DELETE", "OPTIONS", "POST"),
                                requests.stream().map(RecordedRequest::method).toList()),
                () -> assertTrue(requests.stream().allMatch(value -> value.body().isEmpty())),
                () -> assertTrue(requests.stream().allMatch(value -> value.contentType() == null)));
    }

    @Test
    void responseShouldBeRejectedBeforeReadingBeyondConfiguredLimit() throws Exception {
        start(exchange -> respond(exchange, 200, "x".repeat(2048)));
        HttpServiceClient client = client(Map.of());
        ServiceRequest request = request("GET", "large", null);
        request.setMaxResponseBytes(1024);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> client.invoke(request));

        assertTrue(exception.getMessage().contains("maxResponseBytes=1024"));
    }

    @Test
    void nonSuccessfulStatusShouldRemainAvailableForRunnerDecision() throws Exception {
        start(exchange -> respond(exchange, 503, "unavailable"));
        HttpServiceClient client = client(Map.of());

        ServiceResponse response = client.invoke(request("GET", "health", null));

        assertEquals(503, response.getStatus());
        assertEquals("unavailable", response.getBody());
        assertFalse(client.success(response));
    }

    @Test
    void oauth2ShouldApplyBearerAndRejectAuthorizationOverride() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        AtomicInteger invalidations = new AtomicInteger();
        start(
                exchange -> {
                    recorded.set(record(exchange));
                    respond(exchange, 401, "expired");
                });
        OAuth2TokenProvider tokenProvider =
                new OAuth2TokenProvider() {
                    @Override
                    public String getBearerToken() {
                        return "oauth-token";
                    }

                    @Override
                    public void invalidate() {
                        invalidations.incrementAndGet();
                    }
                };
        HttpServiceClient client =
                new HttpServiceClient(new OkHttpClient(), endpoint(), Map.of(), tokenProvider);
        clients.add(client);

        client.invoke(request("GET", null, null));

        assertEquals("Bearer oauth-token", recorded.get().authorization());
        assertEquals(1, invalidations.get());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        client.newInvocation(
                                request("GET", null, Map.of("authorization", "manual"))));
    }

    @Test
    void closingInvocationShouldCancelBlockedCallWithoutServerResponse() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        start(
                exchange -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                        respond(exchange, 200, "late");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        exchange.close();
                    }
                });
        HttpServiceClient client = client(Map.of());
        ServiceInvocation invocation = client.newInvocation(request("GET", "blocking", null));
        ExecutorService executor = register(Executors.newSingleThreadExecutor());
        Future<ServiceResponse> future = executor.submit(invocation::execute);

        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            invocation.close();
            ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof BusinessException);
        } finally {
            release.countDown();
        }
    }

    @Test
    void oauthInvocationCreationMustNotStartTokenIo() {
        var calls = new AtomicInteger();
        OAuth2TokenProvider provider =
                new OAuth2TokenProvider() {
                    public String getBearerToken() {
                        calls.incrementAndGet();
                        return "token";
                    }

                    public void invalidate() {}
                };
        var http =
                new HttpServiceClient(new OkHttpClient(), "http://127.0.0.1:1", Map.of(), provider);
        clients.add(http);
        try (var invocation = http.newInvocation(request("GET", null, null))) {
            assertEquals(0, calls.get(), "invocation must be registered before token IO");
        }
    }

    @Test
    void stoppingColdTokenRequestMustReleaseCallerWithoutSendingBusinessRequest() throws Exception {
        var tokenStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var businessCalls = new AtomicInteger();
        start(
                exchange -> {
                    if (exchange.getRequestURI().getPath().equals("/token")) {
                        tokenStarted.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        respond(exchange, 200, "{\"access_token\":\"token\",\"expires_in\":3600}");
                    } else {
                        businessCalls.incrementAndGet();
                        respond(exchange, 200, "ok");
                    }
                });
        var config =
                new com.ifelio.ijob.core.resource.factory.service.OAuth2ClientCredentialsConfig();
        config.setTokenEndpoint(endpoint() + "/token");
        config.setClientId("test-client");
        config.setClientSecret("test-secret");
        config.setClientAuthenticationMethod(
                com.ifelio.ijob.core.resource.factory.service.OAuth2ClientAuthenticationMethod
                        .CLIENT_SECRET_BASIC);
        var transport = new OkHttpClient();
        var http =
                new HttpServiceClient(
                        transport,
                        endpoint() + "/business",
                        Map.of(),
                        new OAuth2ClientCredentialsTokenProvider(transport, config));
        clients.add(http);
        var executor = register(Executors.newSingleThreadExecutor());
        try {
            var creation = executor.submit(() -> http.newInvocation(request("POST", null, null)));
            try (var invocation = creation.get(2, TimeUnit.SECONDS)) {
                var result = executor.submit(invocation::execute);
                assertTrue(tokenStarted.await(2, TimeUnit.SECONDS));
                invocation.close();
                assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
                assertEquals(0, businessCalls.get());
            }
        } finally {
            release.countDown();
        }
    }

    private HttpServiceClient client(Map<String, String> headers) {
        HttpServiceClient client =
                new HttpServiceClient(new OkHttpClient(), endpoint() + "/base/", headers);
        clients.add(client);
        return client;
    }

    private ServiceRequest request(String method, String path, Map<String, String> headers) {
        ServiceRequest request = new ServiceRequest();
        request.setMethod(method);
        request.setPath(path);
        request.setHeaders(headers);
        return request;
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.setExecutor(register(Executors.newCachedThreadPool()));
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private RecordedRequest record(HttpExchange exchange) throws IOException {
        return new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Default"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String header(ServiceResponse response, String name) {
        return response.getHeaders().entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private <T extends ExecutorService> T register(T executor) {
        executors.add(executor);
        return executor;
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private record RecordedRequest(
            String method,
            String path,
            String authorization,
            String defaultHeader,
            String contentType,
            String body) {}
}
