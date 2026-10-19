package com.ifielsc.ijob.core.resource.factory.service.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResourceConfig;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceConfig;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

class WebServiceClientTest {
    @Test
    void controlRetryAfterCannotReplaySoapRequest() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        start(
                exchange -> {
                    calls.incrementAndGet();
                    exchange.getResponseHeaders().add("Retry-After", "0");
                    respond(exchange, 503, "unavailable");
                });
        client = new WebServiceClient(new OkHttpClient(), config());
        var request = request("stop", "urn:stop", Map.of());
        request.setControlRequest(true);
        try {
            client.invoke(request);
        } catch (com.ifielsc.ijob.common.exception.BusinessException expected) {
            /* Replay refused locally. */
        }
        assertEquals(1, calls.get());
    }

    private HttpServer server;
    private ExecutorService serverExecutor;
    private WebServiceClient client;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (client != null) {
            client.close();
        }
    }

    @Test
    void invokeShouldSendActionMergedHeadersAndEscapedEnvelope() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> action = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        start(
                exchange -> {
                    body.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    action.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
                    token.set(exchange.getRequestHeaders().getFirst("X-Token"));
                    respond(exchange, 200, "<soap:Envelope/>");
                });
        ServiceResourceConfig config = config();
        config.setHeaders(Map.of("X-Token", "resource"));
        client = new WebServiceClient(new OkHttpClient(), config);
        ServiceRequest request = request("lookup", "urn:lookup", Map.of("name", "A&B <admin>"));
        request.setHeaders(Map.of("X-Token", "task"));

        ServiceResponse response = client.invoke(request);

        assertAll(
                () -> assertEquals(200, response.getStatus()),
                () -> assertEquals("urn:lookup", action.get()),
                () -> assertEquals("task", token.get()),
                () -> assertTrue(body.get().contains("xmlns:ns=\"urn:test&amp;accept\"")),
                () -> assertTrue(body.get().contains("<name>A&amp;B &lt;admin&gt;</name>")),
                () -> assertTrue(client.success(response)));
    }

    @Test
    void soapFaultWithNamespaceShouldFailBusinessTransportCheck() {
        client = new WebServiceClient(new OkHttpClient(), config());
        ServiceResponse response = new ServiceResponse();
        response.setStatus(200);
        response.setBody("<soap:Fault><message>denied</message></soap:Fault>");

        assertFalse(client.success(response));
    }

    @Test
    void invalidResolvedOperationShouldBeRejectedBeforeNetworkCall() {
        client = new WebServiceClient(new OkHttpClient(), config());
        ServiceRequest request = request("bad operation", null, Map.of());

        assertThrows(IllegalArgumentException.class, () -> client.newInvocation(request));
    }

    private ServiceResourceConfig config() {
        ServiceResourceConfig config = new ServiceResourceConfig();
        config.setEndpoint(server == null ? "http://127.0.0.1:1/service" : endpoint());
        WebServiceConfig webService = new WebServiceConfig();
        webService.setNamespace("urn:test&accept");
        webService.setWsdl(config.getEndpoint() + "?wsdl");
        config.setWebService(webService);
        return config;
    }

    private ServiceRequest request(
            String operation, String action, Map<String, Object> parameters) {
        WebServiceRequest webService = new WebServiceRequest();
        webService.setOperation(operation);
        webService.setAction(action);
        webService.setParameters(parameters);
        ServiceRequest request = new ServiceRequest();
        request.setWebService(webService);
        return request;
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/service";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
