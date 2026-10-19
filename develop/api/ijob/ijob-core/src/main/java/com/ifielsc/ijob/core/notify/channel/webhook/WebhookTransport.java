// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.NotifyAuditFailure;
import com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException;

import jakarta.annotation.PreDestroy;

import okhttp3.Dns;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Dedicated bounded client: no redirects, automatic replays, cookies or private DNS targets. */
@Component
@lombok.extern.slf4j.Slf4j
public class WebhookTransport {
    private final ObjectMapper json;
    private final OkHttpClient http;
    private com.ifielsc.ijob.core.notify.NotifyRateLimitService rateLimits;

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookTransport(
            ObjectMapper json, com.ifielsc.ijob.core.notify.NotifyRateLimitService rateLimits) {
        this(json);
        this.rateLimits = rateLimits;
    }

    public WebhookTransport(ObjectMapper json) {
        this.json = json;
        this.http =
                new OkHttpClient.Builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(10))
                        .callTimeout(Duration.ofSeconds(15))
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .retryOnConnectionFailure(false)
                        .proxy(java.net.Proxy.NO_PROXY)
                        .dns(
                                host -> {
                                    var addresses = Dns.SYSTEM.lookup(host);
                                    for (InetAddress address : addresses) {
                                        byte[] raw = address.getAddress();
                                        if (address.isAnyLocalAddress()
                                                || address.isLoopbackAddress()
                                                || address.isLinkLocalAddress()
                                                || address.isSiteLocalAddress()
                                                || address.isMulticastAddress()
                                                || (raw.length == 16 && (raw[0] & 0xfe) == 0xfc)
                                                || (raw.length == 4
                                                        && (raw[0] & 255) == 100
                                                        && (raw[1] & 255) >= 64
                                                        && (raw[1] & 255) <= 127))
                                            throw new java.net.UnknownHostException(
                                                    "Notification endpoint is not public");
                                    }
                                    return addresses;
                                })
                        .build();
    }

    public void send(NotifyType type, URI uri, String body) {
        Request request =
                new Request.Builder()
                        .url(uri.toString())
                        .post(
                                RequestBody.create(
                                        body, MediaType.get("application/json; charset=utf-8")))
                        .build();
        try (var response = http.newCall(request).execute()) {
            byte[] bytes =
                    response.body() == null
                            ? new byte[0]
                            : response.body().byteStream().readNBytes(65537);
            if (bytes.length > 65536)
                throw failure(NotifyAuditFailure.RESPONSE_INVALID, response.code(), 0, false);
            classify(
                    type,
                    response.code(),
                    response.header("Retry-After"),
                    response.header("X-RateLimit-Remaining"),
                    response.header("X-RateLimit-Reset-After"),
                    new String(bytes, StandardCharsets.UTF_8));
            if (type == NotifyType.DISCORD
                    && rateLimits != null
                    && "0".equals(response.header("X-RateLimit-Remaining"))) {
                long delay = retryMillis(response.header("X-RateLimit-Reset-After"));
                if (delay > 0) {
                    var config = new com.ifielsc.ijob.infra.entity.notify.NotifyConfig();
                    config.setType(type);
                    config.setConfig(
                            json.writeValueAsString(
                                    java.util.Map.of(
                                            "webhook",
                                            "https://" + uri.getHost() + uri.getRawPath())));
                    try {
                        rateLimits.cooldown(config, delay, false);
                    } catch (RuntimeException e) {
                        log.warn(
                                "notification cooldown persistence failed after provider"
                                        + " acceptance, type={}",
                                type);
                    }
                }
            }
        } catch (NotifyDeliveryException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw failure(NotifyAuditFailure.DELIVERY_TIMEOUT, null, 0, false);
        } catch (IOException e) {
            throw failure(
                    com.ifielsc.ijob.core.execute.TimeoutExceptionClassifier.isTimeout(e)
                            ? NotifyAuditFailure.DELIVERY_TIMEOUT
                            : NotifyAuditFailure.TRANSPORT_FAILED,
                    null,
                    0,
                    false);
        }
    }

    public void classify(
            NotifyType type,
            int status,
            String retryAfter,
            String remaining,
            String resetAfter,
            String body) {
        JsonNode parsed = null;
        if (type != NotifyType.SLACK)
            try {
                parsed = json.readTree(body);
            } catch (IOException ignored) {
            }
        if (status == 429) {
            long delay = retryMillis(retryAfter);
            if (parsed != null && parsed.path("retry_after").isNumber())
                delay = Math.max(delay, seconds(parsed.path("retry_after").asDouble()));
            if (delay > Duration.ofDays(7).toMillis())
                throw failure(NotifyAuditFailure.REMOTE_REJECTED, status, 0, false);
            throw failure(
                    NotifyAuditFailure.RATE_LIMITED,
                    status,
                    Math.max(1000, delay),
                    parsed != null && parsed.path("global").asBoolean());
        }
        if (status < 200 || status >= 300) {
            long delay = retryMillis(retryAfter);
            if (delay > Duration.ofDays(7).toMillis())
                throw failure(NotifyAuditFailure.REMOTE_REJECTED, status, 0, false);
            throw failure(
                    status == 408 || status >= 500
                            ? NotifyAuditFailure.REMOTE_UNAVAILABLE
                            : NotifyAuditFailure.REMOTE_REJECTED,
                    status,
                    delay,
                    false);
        }
        if (type == NotifyType.SLACK) {
            if (status != 200 || !"ok".equals(body.trim()))
                throw failure(NotifyAuditFailure.REMOTE_REJECTED, status, 0, false);
            return;
        }
        if (parsed == null) throw failure(NotifyAuditFailure.RESPONSE_INVALID, status, 0, false);
        if (type == NotifyType.DISCORD) {
            if (!parsed.path("id").asText().matches("[0-9]+"))
                throw failure(NotifyAuditFailure.RESPONSE_INVALID, status, 0, false);
            return;
        }
        JsonNode code = parsed.get(type == NotifyType.FEISHU ? "code" : "errcode");
        if (code == null || !code.isIntegralNumber())
            throw failure(NotifyAuditFailure.RESPONSE_INVALID, status, 0, false);
        if (code.bigIntegerValue().signum() == 0) return;
        String value = code.asText();
        if ((type == NotifyType.WECOM && "45009".equals(value))
                || (type == NotifyType.FEISHU && "11232".equals(value))
                || (type == NotifyType.DINGTALK && "130101".equals(value)))
            throw failure(NotifyAuditFailure.RATE_LIMITED, status, 60000, false);
        boolean permanent =
                switch (type) {
                    case DINGTALK -> java.util.Set.of("310000", "40035").contains(value);
                    case WECOM ->
                            java.util.Set.of("40014", "93000", "93004", "40058", "40008")
                                    .contains(value);
                    case FEISHU ->
                            java.util.Set.of("19021", "19022", "19024", "19025", "19026", "19027")
                                    .contains(value);
                    default -> false;
                };
        throw failure(
                permanent ? NotifyAuditFailure.REMOTE_REJECTED : NotifyAuditFailure.REMOTE_FAILURE,
                status,
                0,
                false);
    }

    public static long retryMillis(String value) {
        if (value == null) return 0;
        try {
            return seconds(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            try {
                return Math.max(
                        0,
                        Duration.between(
                                        Instant.now(),
                                        ZonedDateTime.parse(
                                                        value, DateTimeFormatter.RFC_1123_DATE_TIME)
                                                .toInstant())
                                .toMillis());
            } catch (RuntimeException e) {
                return 0;
            }
        }
    }

    private static long seconds(double value) {
        return Double.isFinite(value) && value > 0
                ? (long) Math.min(Long.MAX_VALUE / 1000.0, Math.ceil(value * 1000))
                : 0;
    }

    private static NotifyDeliveryException failure(
            NotifyAuditFailure kind, Integer status, long delay, boolean global) {
        return new NotifyDeliveryException(kind, status, delay, global);
    }

    @PreDestroy
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
