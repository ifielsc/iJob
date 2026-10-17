package com.ifelio.ijob.core.resource.factory.monitor.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.contract.monitor.AgentInfo;
import com.ifelio.ijob.contract.monitor.MonitorReport;
import com.ifelio.ijob.core.resource.factory.monitor.MonitorServerResourceConfig;

import lombok.extern.slf4j.Slf4j;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class HttpMonitorClient implements MonitorClient {
    static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final MonitorServerResourceConfig config;

    private final ObjectMapper objectMapper;

    private final OkHttpClient client;

    private final AtomicBoolean closed = new AtomicBoolean();

    public HttpMonitorClient(MonitorServerResourceConfig config, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.client =
                new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                        .readTimeout(Duration.ofMillis(config.getReadTimeout()))
                        .build();
    }

    @Override
    public boolean ping() {
        Request request = requestBuilder("/api/monitor/ping").get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }

            com.ifelio.ijob.contract.monitor.PingResponse result =
                    objectMapper.readValue(
                            responseBody(response),
                            com.ifelio.ijob.contract.monitor.PingResponse.class);
            return result.isSuccess();
        } catch (Exception exception) {
            log.warn(
                    "Ping monitor agent failed",
                    com.ifelio.ijob.common.diagnostic.SafeDiagnostic.sanitized(exception));
            return false;
        }
    }

    @Override
    public AgentInfo getAgentInfo() {
        Request request = requestBuilder("/api/monitor/info").get().build();
        try (Response response = client.newCall(request).execute()) {
            checkResponse(response);

            return objectMapper.readValue(responseBody(response), AgentInfo.class);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Monitor agent info request failed", e);
        }
    }

    @Override
    public MonitorReport collect(
            com.ifelio.ijob.contract.monitor.MonitorCollectRequest requestBody) {
        try {
            RequestBody body =
                    RequestBody.create(
                            objectMapper.writeValueAsBytes(requestBody),
                            MediaType.parse("application/json"));

            Request request = requestBuilder("/api/monitor/collect").post(body).build();
            try (Response response = client.newCall(request).execute()) {
                checkResponse(response);

                return objectMapper.readValue(responseBody(response), MonitorReport.class);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Monitor metrics request failed", e);
        }
    }

    private Request.Builder requestBuilder(String path) {
        String url = buildUrl(path);
        Request.Builder builder = new Request.Builder().url(url);
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(builder::header);
        }
        if (StringUtils.hasText(config.getToken())) {
            builder.header("Authorization", "Bearer " + config.getToken());
        }
        return builder;
    }

    private String buildUrl(String path) {
        String endpoint = config.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + path;
    }

    private void checkResponse(Response response) throws IOException {
        if (response.code() == 408 || response.code() == 504) {
            throw new SocketTimeoutException("Monitor agent request timed out");
        }
        if (!response.isSuccessful()) {
            int status = response.code();
            boolean retryable = status == 429 || status >= 500;
            throw new MonitorClientException("Monitor agent returned HTTP " + status, retryable);
        }
    }

    private byte[] responseBody(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Monitor agent response body is empty");
        }
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            throw new IOException("Monitor agent response body is too large");
        }
        try (InputStream input = body.byteStream()) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new IOException("Monitor agent response body is too large");
            }
            return bytes;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            client.dispatcher().cancelAll();
            client.dispatcher().executorService().shutdownNow();
            client.connectionPool().evictAll();
        }
    }
}
