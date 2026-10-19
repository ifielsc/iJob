package com.ifielsc.ijob.core.resource.factory.service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.resource.factory.service.OAuth2ClientAuthenticationMethod;
import com.ifielsc.ijob.core.resource.factory.service.OAuth2ClientCredentialsConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class OAuth2ClientCredentialsTokenProviderTest {
    private HttpServer server;
    private OkHttpClient client;
    private ExecutorService serverExecutor;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
        }
        if (client != null) {
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void basicAuthenticationShouldFetchAndCacheBearerToken() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        start(
                exchange -> {
                    calls.incrementAndGet();
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    body.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    respond(
                            exchange,
                            200,
                            "{\"access_token\":\"issued-token\","
                                    + "\"token_type\":\"Bearer\","
                                    + "\"expires_in\":3600}");
                });
        OAuth2ClientCredentialsTokenProvider provider =
                provider(OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC);

        assertEquals("issued-token", provider.getBearerToken());
        assertEquals("issued-token", provider.getBearerToken());
        assertEquals(1, calls.get());
        assertEquals(
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString(
                                        "client-id:client-secret".getBytes(StandardCharsets.UTF_8)),
                authorization.get());
        assertTrue(body.get().contains("grant_type=client_credentials"));
        assertTrue(body.get().contains("scope=orders.read"));
    }

    @Test
    void postAuthenticationShouldPutCredentialsInForm() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        start(
                exchange -> {
                    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    body.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    respond(
                            exchange,
                            200,
                            "{\"access_token\":\"posted-token\"," + "\"expires_in\":60}");
                });

        assertEquals(
                "posted-token",
                provider(OAuth2ClientAuthenticationMethod.CLIENT_SECRET_POST).getBearerToken());
        assertEquals(null, authorization.get());
        assertTrue(body.get().contains("client_id=client-id"));
        assertTrue(body.get().contains("client_secret=client-secret"));
    }

    @Test
    void invalidTokenTypeShouldFailWithoutExposingResponse() throws Exception {
        start(
                exchange ->
                        respond(
                                exchange,
                                200,
                                "{\"access_token\":\"secret-value\"," + "\"token_type\":\"MAC\"}"));

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                provider(OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                                        .getBearerToken());

        assertEquals("OAuth2 client credentials token request failed", failure.getMessage());
    }

    @Test
    void cancelledRefreshWaiterMustNotCancelOwnerOrPoisonCachedToken() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new AtomicInteger();
        start(
                exchange -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    }
                    respond(
                            exchange,
                            200,
                            "{\"access_token\":\"owner-token\",\"expires_in\":3600}");
                });
        var provider = provider(OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        var callers = Executors.newFixedThreadPool(2);
        try (var ownerScope = new ServiceCallCancellation();
                var waiterScope = new ServiceCallCancellation()) {
            var owner = callers.submit(() -> provider.getBearerToken(ownerScope));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var waiterEntered = new java.util.concurrent.CountDownLatch(1);
            var waiter =
                    callers.submit(
                            () -> {
                                waiterEntered.countDown();
                                return provider.getBearerToken(waiterScope);
                            });
            assertTrue(waiterEntered.await(1, TimeUnit.SECONDS));
            waiterScope.close();
            assertThrows(ExecutionException.class, () -> waiter.get(1, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            release.countDown();
            assertEquals("owner-token", owner.get(2, TimeUnit.SECONDS));
            assertEquals("owner-token", provider.getBearerToken());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    private OAuth2ClientCredentialsTokenProvider provider(OAuth2ClientAuthenticationMethod method) {
        OAuth2ClientCredentialsConfig config = new OAuth2ClientCredentialsConfig();
        config.setTokenEndpoint(endpoint());
        config.setClientId("client-id");
        config.setClientSecret("client-secret");
        config.setScopes("orders.read");
        config.setClientAuthenticationMethod(method);
        client = new OkHttpClient();
        return new OAuth2ClientCredentialsTokenProvider(client, config);
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", handler::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
    }

    private void respond(HttpExchange exchange, int status, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
