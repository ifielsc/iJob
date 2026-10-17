package com.ifelio.ijob.core.resource.factory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.hutool.json.JSONUtil;

import okhttp3.OkHttpClient;

import org.junit.jupiter.api.Test;

class ServiceResourceFactoryTest {
    @Test
    void shouldApplyEveryConfiguredHttpTimeout() {
        ServiceResourceConfig config = new ServiceResourceConfig();
        config.setConnectTimeout(4);
        config.setReadTimeout(15);
        config.setWriteTimeout(12);
        config.setCallTimeout(25);

        OkHttpClient client = new ServiceResourceFactory().buildHttpClient(config);

        assertEquals(4_000, client.connectTimeoutMillis());
        assertEquals(15_000, client.readTimeoutMillis());
        assertEquals(12_000, client.writeTimeoutMillis());
        assertEquals(25_000, client.callTimeoutMillis());
    }

    @Test
    void legacyConfigShouldRetainNewTimeoutDefaults() {
        ServiceResourceConfig config =
                JSONUtil.toBean(
                        "{\"protocol\":\"HTTP\",\"connectTimeout\":4," + "\"readTimeout\":15}",
                        ServiceResourceConfig.class);

        OkHttpClient client = new ServiceResourceFactory().buildHttpClient(config);

        assertEquals(4_000, client.connectTimeoutMillis());
        assertEquals(15_000, client.readTimeoutMillis());
        assertEquals(30_000, client.writeTimeoutMillis());
        assertEquals(60_000, client.callTimeoutMillis());
    }
}
