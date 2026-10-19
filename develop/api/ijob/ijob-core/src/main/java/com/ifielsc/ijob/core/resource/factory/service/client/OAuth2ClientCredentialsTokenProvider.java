// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.service.client;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.ifielsc.ijob.core.resource.factory.service.OAuth2ClientAuthenticationMethod;
import com.ifielsc.ijob.core.resource.factory.service.OAuth2ClientCredentialsConfig;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import okio.Buffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public class OAuth2ClientCredentialsTokenProvider implements OAuth2TokenProvider {
    private static final long MAX_TOKEN_RESPONSE_BYTES = 256 * 1024L;
    private static final int DEFAULT_EXPIRES_IN_SECONDS = 300;
    private static final int MAX_ACCESS_TOKEN_LENGTH = 64 * 1024;

    private final OkHttpClient client;
    private final OAuth2ClientCredentialsConfig config;
    private final LongSupplier clock;
    private volatile CachedToken cachedToken;
    private final ReentrantLock refreshLock = new ReentrantLock();

    public OAuth2ClientCredentialsTokenProvider(
            OkHttpClient client, OAuth2ClientCredentialsConfig config) {
        this(client, config, System::currentTimeMillis);
    }

    OAuth2ClientCredentialsTokenProvider(
            OkHttpClient client, OAuth2ClientCredentialsConfig config, LongSupplier clock) {
        this.client = client;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public String getBearerToken() {
        try (var cancellation = new ServiceCallCancellation()) {
            return getBearerToken(cancellation);
        }
    }

    @Override
    public String getBearerToken(ServiceCallCancellation cancellation) {
        cancellation.check();
        CachedToken current = cachedToken;
        long now = clock.getAsLong();
        if (current != null && now < current.refreshAtMillis()) return current.value();
        try {
            // A cancelled waiter must not interrupt another invocation's token request.
            while (!refreshLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                cancellation.check();
            }
            try {
                cancellation.check();
                current = cachedToken;
                now = clock.getAsLong();
                if (current != null && now < current.refreshAtMillis()) return current.value();
                cachedToken = requestToken(now, cancellation);
                return cachedToken.value();
            } finally {
                refreshLock.unlock();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("OAuth2 token wait interrupted");
        }
    }

    @Override
    public void invalidate() {
        cachedToken = null;
    }

    private CachedToken requestToken(long now, ServiceCallCancellation cancellation) {
        FormBody.Builder form =
                new FormBody.Builder(StandardCharsets.UTF_8)
                        .add("grant_type", "client_credentials");
        addOptional(form, "scope", config.getScopes());
        addOptional(form, "audience", config.getAudience());
        if (config.getClientAuthenticationMethod()
                == OAuth2ClientAuthenticationMethod.CLIENT_SECRET_POST) {
            form.add("client_id", config.getClientId());
            form.add("client_secret", config.getClientSecret());
        }
        Request.Builder request =
                new Request.Builder().url(config.getTokenEndpoint()).post(form.build());
        if (config.getClientAuthenticationMethod()
                == OAuth2ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
            request.header(
                    "Authorization",
                    Credentials.basic(
                            config.getClientId(),
                            config.getClientSecret(),
                            StandardCharsets.UTF_8));
        }

        Call call = client.newCall(request.build());
        cancellation.attach(call);
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "OAuth2 token endpoint returned HTTP " + response.code());
            }
            JSONObject json = JSONUtil.parseObj(readBounded(response.body()));
            String tokenType = json.getStr("token_type", "Bearer");
            String accessToken = json.getStr("access_token");
            if (!"Bearer".equalsIgnoreCase(tokenType)
                    || accessToken == null
                    || accessToken.isBlank()
                    || accessToken.length() > MAX_ACCESS_TOKEN_LENGTH) {
                throw new IllegalStateException("OAuth2 token response is invalid");
            }
            int expiresIn = expiresIn(json);
            long lifetimeMillis = expiresIn * 1000L;
            long skewMillis = Math.min(config.getRefreshSkewSeconds() * 1000L, lifetimeMillis / 2L);
            return new CachedToken(accessToken, now + lifetimeMillis - skewMillis);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                    "OAuth2 client credentials token request failed", exception);
        }
    }

    private void addOptional(FormBody.Builder form, String name, String value) {
        if (value != null && !value.isBlank()) {
            form.add(name, value);
        }
    }

    private String readBounded(ResponseBody body) throws IOException {
        if (body == null || body.contentLength() > MAX_TOKEN_RESPONSE_BYTES) {
            throw new IOException("OAuth2 token response is too large");
        }
        Buffer buffer = new Buffer();
        long total = 0;
        while (total <= MAX_TOKEN_RESPONSE_BYTES) {
            long read = body.source().read(buffer, MAX_TOKEN_RESPONSE_BYTES + 1L - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        byte[] bytes = buffer.readByteArray();
        if (bytes.length > MAX_TOKEN_RESPONSE_BYTES) {
            throw new IOException("OAuth2 token response is too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int expiresIn(JSONObject json) {
        Integer value;
        try {
            value = json.getInt("expires_in");
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OAuth2 expires_in is invalid", exception);
        }
        if (value == null) {
            return DEFAULT_EXPIRES_IN_SECONDS;
        }
        if (value < 1 || value > 604800) {
            throw new IllegalStateException("OAuth2 expires_in is invalid");
        }
        return value;
    }

    private record CachedToken(String value, long refreshAtMillis) {}
}
