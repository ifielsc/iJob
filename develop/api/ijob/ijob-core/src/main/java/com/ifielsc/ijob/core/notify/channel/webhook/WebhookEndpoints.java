package com.ifielsc.ijob.core.notify.channel.webhook;

import com.ifielsc.ijob.contract.enums.NotifyType;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** New providers use exact routes; existing DingTalk HTTPS configurations remain compatible. */
public final class WebhookEndpoints {
    private WebhookEndpoints() {}

    public static URI require(NotifyType type, String value) {
        try {
            URI uri = URI.create(value);
            if (value.length() > 2048
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || (type != NotifyType.DINGTALK && uri.getPort() != -1 && uri.getPort() != 443))
                throw new IllegalArgumentException();
            // DingTalk historically supports HTTPS relay URLs. Preserve those configurations;
            // the transport still rejects redirects and non-public resolved addresses.
            if (type == NotifyType.DINGTALK) return uri;
            String path = uri.getRawPath();
            Map<String, String> query = query(uri);
            boolean valid =
                    switch (type) {
                        case DINGTALK ->
                                "oapi.dingtalk.com".equalsIgnoreCase(uri.getHost())
                                        && "/robot/send".equals(path)
                                        && query.size() == 1
                                        && token(query.get("access_token"));
                        case WECOM ->
                                "qyapi.weixin.qq.com".equalsIgnoreCase(uri.getHost())
                                        && "/cgi-bin/webhook/send".equals(path)
                                        && query.size() == 1
                                        && token(query.get("key"));
                        case FEISHU ->
                                "open.feishu.cn".equalsIgnoreCase(uri.getHost())
                                        && path.matches("/open-apis/bot/v2/hook/[A-Za-z0-9_-]+")
                                        && query.isEmpty();
                        case SLACK ->
                                "hooks.slack.com".equalsIgnoreCase(uri.getHost())
                                        && path.matches(
                                                "/services/[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9_-]+")
                                        && query.isEmpty();
                        case DISCORD ->
                                "discord.com".equalsIgnoreCase(uri.getHost())
                                        && path.matches(
                                                "/api(?:/v10)?/webhooks/[0-9]+/[A-Za-z0-9._-]+")
                                        && query.isEmpty();
                        default -> false;
                    };
            if (!valid) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("Invalid notification provider webhook");
        }
    }

    public static String identity(NotifyType type, String url) {
        URI uri = require(type, url);
        return switch (type) {
            case DINGTALK -> dingTalkIdentity(uri);
            case WECOM -> query(uri).get("key");
            case DISCORD -> uri.getPath().replaceFirst("/api(?:/v10)?/webhooks/", "").split("/")[0];
            default -> uri.getPath();
        };
    }

    private static String dingTalkIdentity(URI uri) {
        if ("oapi.dingtalk.com".equalsIgnoreCase(uri.getHost())
                && "/robot/send".equals(uri.getPath())) {
            try {
                String token = query(uri).get("access_token");
                if (token(token)) return token;
            } catch (IllegalArgumentException ignored) {
                // Nonstandard legacy query strings retain their full destination identity.
            }
        }
        return "url:" + uri.normalize();
    }

    public static String display(NotifyType type, String url) {
        URI uri = require(type, url);
        return "https://"
                + uri.getHost()
                + switch (type) {
                    case DINGTALK, WECOM -> uri.getPath() + "?••••";
                    case FEISHU -> "/open-apis/bot/v2/hook/••••";
                    case SLACK -> "/services/••••/••••/••••";
                    case DISCORD -> "/api/webhooks/••••/••••";
                    default -> "";
                };
    }

    private static boolean token(String text) {
        return text != null && text.matches("[A-Za-z0-9_-]+");
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new HashMap<>();
        if (uri.getRawQuery() == null) return values;
        for (String pair : uri.getRawQuery().split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException();
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException();
        }
        return values;
    }
}
