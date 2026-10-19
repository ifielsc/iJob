// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.channel.webhook.WebhookEndpoints;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.mapper.coordination.DatabaseClockMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyRateLimitMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Database CAS serializes destination budgets across configs and workers. No process-local quota.
 */
@Service
@RequiredArgsConstructor
public class NotifyRateLimitService {
    private final NotifyRateLimitMapper mapper;
    private final DatabaseClockMapper clock;
    private final ObjectMapper json;

    public long acquire(NotifyConfig config) {
        if (config.getType() == NotifyType.EMAIL) return 0;
        long now = now();
        List<String> keys = keys(config);
        for (String key : keys) {
            mapper.insertIfAbsent(key);
            Long deadline = mapper.selectDeadline(key);
            if (deadline == null)
                throw new IllegalStateException("Notification rate state missing");
            if (deadline > now) return deadline - now;
        }
        long interval =
                switch (config.getType()) {
                    case DINGTALK, WECOM -> 3100;
                    case FEISHU -> 650;
                    case SLACK -> 1100;
                    case DISCORD -> 1000;
                    default -> 0;
                };
        // A partially acquired multi-key budget is intentionally conservative; it cannot oversend.
        for (String key : keys) {
            if (key.equals(digest("DISCORD:global"))) continue;
            if (mapper.acquire(key, now, now + interval) != 1) {
                Long deadline = mapper.selectDeadline(key);
                return Math.max(1, deadline == null ? interval : deadline - now);
            }
        }
        return 0;
    }

    public void cooldown(NotifyConfig config, long delayMillis, boolean global) {
        if (config.getType() == NotifyType.EMAIL) return;
        long deadline = Math.addExact(now(), Math.max(1, delayMillis));
        for (String key : keys(config)) {
            if (key.equals(digest("DISCORD:global")) && !global) continue;
            mapper.insertIfAbsent(key);
            mapper.cooldown(key, deadline);
        }
    }

    private List<String> keys(NotifyConfig config) {
        try {
            var value = json.readTree(config.getConfig());
            String webhook = value.path("webhook").asText();
            List<String> keys = new ArrayList<>();
            keys.add(
                    digest(
                            config.getType()
                                    + ":"
                                    + WebhookEndpoints.identity(config.getType(), webhook)));
            if (config.getType() == NotifyType.SLACK
                    && !value.path("channelId").asText().isBlank()) {
                String workspace =
                        WebhookEndpoints.require(config.getType(), webhook).getPath().split("/")[2];
                keys.add(
                        digest(
                                "SLACK:channel:"
                                        + workspace
                                        + ":"
                                        + value.path("channelId").asText()));
            }
            if (config.getType() == NotifyType.DISCORD) keys.add(digest("DISCORD:global"));
            return keys;
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Notification rate target is invalid");
        }
    }

    private long now() {
        return clock.currentTimestamp().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
