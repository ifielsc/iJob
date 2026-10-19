package com.ifielsc.ijob.core.resource.factory.service;

import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.core.resource.factory.service.client.HttpServiceClient;
import com.ifielsc.ijob.core.resource.factory.service.client.OAuth2ClientCredentialsTokenProvider;
import com.ifielsc.ijob.core.resource.factory.service.client.OAuth2TokenProvider;
import com.ifielsc.ijob.core.resource.factory.service.client.WebServiceClient;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import lombok.RequiredArgsConstructor;

import okhttp3.OkHttpClient;

import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ServiceResourceFactory implements ResourceClientFactory {
    @Override
    public ResourceType supportType() {
        return ResourceType.SERVICE;
    }

    @Override
    public Object create(Resource resource) {
        ServiceResourceConfig config =
                JSONUtil.toBean(resource.getConfig(), ServiceResourceConfig.class);
        OkHttpClient client = buildHttpClient(config);
        OAuth2TokenProvider tokenProvider =
                config.getAuthType() == ServiceAuthType.OAUTH2_CLIENT_CREDENTIALS
                        ? new OAuth2ClientCredentialsTokenProvider(client, config.getOauth2())
                        : null;

        return switch (config.getProtocol()) {
            case HTTP ->
                    new HttpServiceClient(
                            client, config.getEndpoint(), config.getHeaders(), tokenProvider);
            case WEB_SERVICE -> new WebServiceClient(client, config);
        };
    }

    @Override
    public void verifyConnection(Object client) {
        if (!((com.ifielsc.ijob.core.resource.factory.service.client.ServiceClient) client)
                .testConnection()) {
            throw new IllegalStateException("service connection unavailable");
        }
    }

    OkHttpClient buildHttpClient(ServiceResourceConfig config) {
        return new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                .callTimeout(config.getCallTimeout(), TimeUnit.SECONDS)
                .build();
    }
}
