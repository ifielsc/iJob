// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.monitor.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.monitor.MonitorCollectRequest;
import com.ifielsc.ijob.contract.monitor.MonitorReport;
import com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier;
import com.ifielsc.ijob.core.resource.factory.monitor.MonitorServerResourceConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class HttpMonitorClientTest {
    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService callerExecutor;

    @AfterEach
    void closeResources() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (callerExecutor != null) {
            callerExecutor.shutdownNow();
        }
    }

    @Test
    void shouldCollectThroughLocalHttpWithBearerTokenPrecedence() throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        String[] authorization = new String[1];
        String[] custom = new String[1];
        String[] body = new String[1];
        startServer(
                exchange -> {
                    authorization[0] = exchange.getRequestHeaders().getFirst("Authorization");
                    custom[0] = exchange.getRequestHeaders().getFirst("X-Test");
                    body[0] =
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8);
                    called.countDown();
                    respond(exchange, 200, "{\"success\":true,\"cpu\":{\"usage\":42.5}}");
                },
                "/base/api/monitor/collect");
        MonitorServerResourceConfig config = config();
        config.setEndpoint(endpoint() + "/base/");
        config.setToken("secret-token");
        config.setHeaders(new LinkedHashMap<>());
        config.getHeaders().put("Authorization", "Custom ignored");
        config.getHeaders().put("X-Test", "value");

        try (HttpMonitorClient client = new HttpMonitorClient(config, new ObjectMapper())) {
            MonitorReport report =
                    client.collect(MonitorCollectRequest.builder().collectItems(List.of()).build());

            assertTrue(called.await(1, TimeUnit.SECONDS));
            assertTrue(report.isSuccess());
            assertEquals(42.5, report.getCpu().getUsage());
            assertEquals("Bearer secret-token", authorization[0]);
            assertEquals("value", custom[0]);
            assertTrue(body[0].contains("collectItems"));
        }
    }

    @Test
    void unsuccessfulPingShouldReturnFalse() throws Exception {
        startServer(exchange -> respond(exchange, 503, "unavailable"), "/api/monitor/ping");

        try (HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper())) {
            assertFalse(client.ping());
        }
    }

    @Test
    void oversizedResponseShouldBeRejectedBeforeDeserialization() throws Exception {
        String oversized = "x".repeat(HttpMonitorClient.MAX_RESPONSE_BYTES + 1);
        startServer(exchange -> respond(exchange, 200, oversized), "/api/monitor/collect");

        try (HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper())) {
            assertThrows(
                    RuntimeException.class,
                    () ->
                            client.collect(
                                    MonitorCollectRequest.builder()
                                            .collectItems(List.of())
                                            .build()));
        }
    }

    @Test
    void gatewayTimeoutShouldRemainClassifiableAsTimeout() throws Exception {
        startServer(exchange -> respond(exchange, 504, "timeout"), "/api/monitor/collect");

        try (HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper())) {
            RuntimeException exception =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    client.collect(
                                            MonitorCollectRequest.builder()
                                                    .collectItems(List.of())
                                                    .build()));
            assertTrue(TimeoutExceptionClassifier.isTimeout(exception));
        }
    }

    @Test
    void clientAndServerErrorsShouldExposeRetryClassification() throws Exception {
        startServer(exchange -> respond(exchange, 401, "unauthorized"), "/api/monitor/collect");
        try (HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper())) {
            MonitorClientException exception =
                    assertThrows(
                            MonitorClientException.class,
                            () ->
                                    client.collect(
                                            MonitorCollectRequest.builder()
                                                    .collectItems(List.of())
                                                    .build()));
            assertFalse(exception.isRetryable());
        }

        server.stop(0);
        server = null;
        serverExecutor.shutdownNow();
        serverExecutor = null;
        startServer(exchange -> respond(exchange, 503, "unavailable"), "/api/monitor/collect");
        try (HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper())) {
            MonitorClientException exception =
                    assertThrows(
                            MonitorClientException.class,
                            () ->
                                    client.collect(
                                            MonitorCollectRequest.builder()
                                                    .collectItems(List.of())
                                                    .build()));
            assertTrue(exception.isRetryable());
        }
    }

    @Test
    void closeShouldCancelBlockedCollection() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        startServer(
                exchange -> {
                    entered.countDown();
                    try {
                        release.await();
                        respond(exchange, 200, "{\"success\":true}");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },
                "/api/monitor/collect");
        HttpMonitorClient client = new HttpMonitorClient(config(), new ObjectMapper());
        callerExecutor = Executors.newSingleThreadExecutor();
        Future<MonitorReport> future =
                callerExecutor.submit(
                        () ->
                                client.collect(
                                        MonitorCollectRequest.builder()
                                                .collectItems(List.of())
                                                .build()));
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        client.close();
        client.close();

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        release.countDown();
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler, String path)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, handler);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private MonitorServerResourceConfig config() {
        MonitorServerResourceConfig config = new MonitorServerResourceConfig();
        config.setEndpoint(endpoint());
        config.setConnectTimeout(1000);
        config.setReadTimeout(5000);
        return config;
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String responseBody)
            throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
