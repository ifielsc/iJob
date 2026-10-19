package com.ifielsc.ijob.core.resource.factory.service.client;

import com.ifielsc.ijob.core.resource.factory.service.ServiceRequest;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResourceConfig;
import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.core.resource.factory.service.WebServiceRequest;

import lombok.RequiredArgsConstructor;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiredArgsConstructor
public class WebServiceClient implements ServiceClient {
    private final OkHttpClient client;
    private final ServiceResourceConfig config;

    @Override
    public ServiceResponse invoke(ServiceRequest request) {
        try (ServiceInvocation invocation = newInvocation(request)) {
            return invocation.execute();
        }
    }

    @Override
    public ServiceInvocation newInvocation(ServiceRequest request) {
        if (request.getWebService() == null) {
            throw new IllegalArgumentException("WebService request is required");
        }
        String xml = buildSoapMessage(request);
        Request.Builder builder = new Request.Builder().url(config.getEndpoint());
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(builder::header);
        }
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(builder::header);
        }
        WebServiceRequest webService = request.getWebService();
        if (webService.getAction() != null && !webService.getAction().isBlank()) {
            builder.header("SOAPAction", webService.getAction());
        }
        MediaType contentType = MediaType.parse(header(request, "Content-Type", "text/xml"));
        Request httpRequest = builder.post(RequestBody.create(xml, contentType)).build();
        OkHttpClient invocationClient =
                request.isControlRequest() ? ControlRequestClient.once(client) : client;
        okhttp3.Call call = invocationClient.newCall(httpRequest);
        if (request.getDeadlineNanos() != 0)
            call.timeout().deadlineNanoTime(request.getDeadlineNanos());
        return new OkHttpServiceInvocation(
                call, "webservice invoke failed", request.getMaxResponseBytes());
    }

    private String buildSoapMessage(ServiceRequest request) {
        WebServiceRequest ws = request.getWebService();
        return SoapXmlBuilder.build(config.getWebService(), ws);
    }

    @Override
    public boolean success(ServiceResponse response) {
        if (!Integer.valueOf(200).equals(response.getStatus())) {
            return false;
        }

        String body = response.getBody();
        return body == null || !body.matches("(?s).*<(?:[A-Za-z_][\\w.-]*:)?Fault(?:\\s|>).*");
    }

    @Override
    public boolean testConnection() {
        Request request = new Request.Builder().url(config.getWebService().getWsdl()).get().build();
        try (Response response = client.newCall(request).execute()) {
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

    private String header(ServiceRequest request, String name, String defaultValue) {
        if (request.getHeaders() != null) {
            for (var entry : request.getHeaders().entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        if (config.getHeaders() != null) {
            for (var entry : config.getHeaders().entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return defaultValue;
    }
}
