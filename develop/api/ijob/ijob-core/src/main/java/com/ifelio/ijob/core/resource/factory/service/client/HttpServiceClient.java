package com.ifelio.ijob.core.resource.factory.service.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.core.resource.factory.service.ServiceRequest;
import com.ifelio.ijob.core.resource.factory.service.ServiceResponse;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HttpServiceClient implements ServiceClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final OkHttpClient client;
    private final String endpoint;
    private final Map<String, String> defaultHeaders;
    private final OAuth2TokenProvider tokenProvider;

    public HttpServiceClient(OkHttpClient client, String endpoint) {
        this(client, endpoint, null);
    }

    public HttpServiceClient(
            OkHttpClient client, String endpoint, Map<String, String> defaultHeaders) {
        this(client, endpoint, defaultHeaders, null);
    }

    public HttpServiceClient(
            OkHttpClient client,
            String endpoint,
            Map<String, String> defaultHeaders,
            OAuth2TokenProvider tokenProvider) {
        this.client = client;
        this.endpoint = endpoint;
        this.defaultHeaders =
                defaultHeaders == null ? Map.of() : new LinkedHashMap<>(defaultHeaders);
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ServiceResponse invoke(ServiceRequest request) {
        try (ServiceInvocation invocation = newInvocation(request)) {
            return invocation.execute();
        }
    }

    @Override
    public ServiceInvocation newInvocation(ServiceRequest request) {
        validateOAuth2Headers(request.getHeaders());
        return new ServiceInvocation() {
            private final ServiceCallCancellation cancellation =
                    new ServiceCallCancellation(request.getDeadlineNanos());

            @Override
            public ServiceResponse execute() {
                return executeWithDispatch(() -> {});
            }

            @Override
            public ServiceResponse executeWithDispatch(Runnable beforeSend) {
                cancellation.check();
                Request httpRequest = buildRequest(request, cancellation);
                OkHttpClient invocationClient =
                        request.isControlRequest() ? ControlRequestClient.once(client) : client;
                Call call = invocationClient.newCall(httpRequest);
                cancellation.attach(call);
                beforeSend.run();
                cancellation.check();
                ServiceResponse response =
                        new OkHttpServiceInvocation(
                                        call, "http invoke failed", request.getMaxResponseBytes())
                                .execute();
                if (tokenProvider != null && Integer.valueOf(401).equals(response.getStatus())) {
                    tokenProvider.invalidate();
                }
                return response;
            }

            @Override
            public void close() {
                cancellation.close();
            }
        };
    }

    @Override
    public boolean success(ServiceResponse response) {
        Integer status = response.getStatus();
        return status != null && status >= 200 && status < 300;
    }

    @Override
    public boolean testConnection() {
        Request.Builder builder = new Request.Builder().url(endpoint);
        defaultHeaders.forEach(builder::header);
        applyOAuth2(builder, null, null);
        Request request = builder.head().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401 && tokenProvider != null) {
                tokenProvider.invalidate();
            }
            return response.code() >= 100 && response.code() <= 599;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private String getContentType(Map<String, String> headers) {
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return "application/json";
    }

    private Request buildRequest(ServiceRequest request, ServiceCallCancellation cancellation) {
        Request.Builder builder = new Request.Builder().url(resolveUrl(request.getPath()));
        defaultHeaders.forEach(builder::header);
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(builder::header);
        }
        applyOAuth2(builder, request.getHeaders(), cancellation);

        String method =
                request.getMethod() == null ? "POST" : request.getMethod().toUpperCase(Locale.ROOT);
        if ("GET".equals(method)) {
            return builder.get().build();
        }
        if ("HEAD".equals(method)) {
            return builder.head().build();
        }

        if (request.getBody() == null && ("DELETE".equals(method) || "OPTIONS".equals(method))) {
            return builder.method(method, null).build();
        }

        String json;
        try {
            json = request.getBody() == null ? "" : JSON.writeValueAsString(request.getBody());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid JSON request body");
        }
        MediaType mediaType = MediaType.parse(getContentType(mergedHeaders(request.getHeaders())));
        RequestBody body =
                request.getBody() == null
                        ? RequestBody.create(new byte[0], null)
                        : RequestBody.create(json, mediaType);
        return builder.method(method, body).build();
    }

    private String resolveUrl(String path) {
        if (path == null || path.isBlank()) {
            return endpoint;
        }
        if (path.startsWith("?") || path.startsWith("#")) {
            return endpoint + path;
        }
        if (endpoint.endsWith("/") && path.startsWith("/")) {
            return endpoint + path.substring(1);
        }
        if (!endpoint.endsWith("/") && !path.startsWith("/")) {
            return endpoint + "/" + path;
        }
        return endpoint + path;
    }

    private Map<String, String> mergedHeaders(Map<String, String> requestHeaders) {
        Map<String, String> merged = new LinkedHashMap<>(defaultHeaders);
        if (requestHeaders != null) {
            requestHeaders.forEach(
                    (name, value) -> {
                        merged.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
                        merged.put(name, value);
                    });
        }
        return merged;
    }

    private void applyOAuth2(
            Request.Builder builder,
            Map<String, String> requestHeaders,
            ServiceCallCancellation cancellation) {
        if (tokenProvider == null) return;
        validateOAuth2Headers(requestHeaders);
        String token =
                cancellation == null
                        ? tokenProvider.getBearerToken()
                        : tokenProvider.getBearerToken(cancellation);
        builder.header("Authorization", "Bearer " + token);
    }

    private void validateOAuth2Headers(Map<String, String> requestHeaders) {
        if (tokenProvider != null
                && (containsHeader(defaultHeaders, "Authorization")
                        || containsHeader(requestHeaders, "Authorization"))) {
            throw new IllegalArgumentException(
                    "Authorization header cannot override OAuth2 authentication");
        }
    }

    private boolean containsHeader(Map<String, String> headers, String expected) {
        return headers != null
                && headers.keySet().stream().anyMatch(name -> expected.equalsIgnoreCase(name));
    }
}
