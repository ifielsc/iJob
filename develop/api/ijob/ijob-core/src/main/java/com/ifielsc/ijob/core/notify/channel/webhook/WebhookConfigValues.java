// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.webhook;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigView;

import java.util.List;

public final class WebhookConfigValues {
    private WebhookConfigValues() {}

    public static WebhookConfig build(
            NotifyType type, NotifyConfigRequest.Webhook request, WebhookConfig old) {
        boolean keep = Boolean.TRUE.equals(request.getPreserveExisting()) && old != null;
        WebhookConfig value = presentation(type, request, old, keep);
        String url =
                request.getWebhook() == null && old != null
                        ? old.getWebhook()
                        : request.getWebhook();
        try {
            WebhookEndpoints.require(type, url);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        value.setWebhook(url);
        if (Boolean.TRUE.equals(request.getClearSecret()) && request.getSecret() != null)
            throw invalid();
        String secret =
                Boolean.TRUE.equals(request.getClearSecret())
                        ? null
                        : request.getSecret() == null && old != null
                                ? old.getSecret()
                                : request.getSecret();
        if (secret != null
                && (type != NotifyType.FEISHU || secret.isBlank() || secret.length() > 4096))
            throw invalid();
        value.setSecret(secret);
        return value;
    }

    /** Shared presentation validation for saving and credential-free previews. */
    public static WebhookConfig presentation(
            NotifyType type, NotifyConfigRequest.Webhook request, WebhookConfig old, boolean keep) {
        WebhookConfig value = new WebhookConfig();
        String format =
                request.getFormat() == null
                        ? (keep ? old.getFormat() : "Markdown")
                        : request.getFormat();
        if (type == NotifyType.WECOM && !List.of("Markdown", "text").contains(format))
            throw invalid();
        value.setFormat(
                type == NotifyType.WECOM
                        ? format
                        : type == NotifyType.FEISHU
                                ? "card"
                                : type == NotifyType.SLACK ? "blocks" : "embed");
        value.setMembers(
                ids(
                        request.getMembers() == null && keep
                                ? old.getMembers()
                                : request.getMembers(),
                        type == NotifyType.DISCORD
                                ? "[0-9]{1,20}"
                                : type == NotifyType.SLACK
                                        ? "[UW][A-Z0-9]{2,40}"
                                        : "[A-Za-z0-9_.@-]{1,128}"));
        value.setRoles(
                ids(
                        request.getRoles() == null && keep ? old.getRoles() : request.getRoles(),
                        "[0-9]{1,20}"));
        value.setMobiles(
                ids(
                        request.getMobiles() == null && keep
                                ? old.getMobiles()
                                : request.getMobiles(),
                        "\\+?[0-9]{6,20}"));
        value.setAtAll(
                request.getAtAll() == null && keep
                        ? old.isAtAll()
                        : Boolean.TRUE.equals(request.getAtAll()));
        value.setMentionMode(
                request.getMentionMode() == null
                        ? (keep ? old.getMentionMode() : "none")
                        : request.getMentionMode());
        if (!List.of("none", "members", "here", "channel").contains(value.getMentionMode()))
            throw invalid();
        value.setKeyword(
                text(
                        request.getKeyword() == null && keep
                                ? old.getKeyword()
                                : request.getKeyword(),
                        100));
        value.setDestination(
                text(
                        request.getDestination() == null && keep
                                ? old.getDestination()
                                : request.getDestination(),
                        128));
        value.setChannelId(
                text(
                        request.getChannelId() == null && keep
                                ? old.getChannelId()
                                : request.getChannelId(),
                        64));
        if (value.getChannelId() != null
                && (type != NotifyType.SLACK
                        || !value.getChannelId().matches("[CG][A-Z0-9]{2,40}"))) throw invalid();
        if (type != NotifyType.DISCORD && !value.getRoles().isEmpty()) throw invalid();
        if (type == NotifyType.DISCORD
                && value.getMembers().stream().mapToInt(v -> v.length() + 4).sum()
                                + value.getRoles().stream().mapToInt(v -> v.length() + 5).sum()
                        > 2000) throw invalid();
        if ((type != NotifyType.WECOM || !"text".equals(value.getFormat()))
                && !value.getMobiles().isEmpty()) throw invalid();
        if (value.isAtAll()
                && (type == NotifyType.DISCORD
                        || type == NotifyType.SLACK
                        || (type == NotifyType.WECOM && !"text".equals(value.getFormat()))))
            throw invalid();
        if (type != NotifyType.FEISHU && value.getKeyword() != null) throw invalid();
        return value;
    }

    public static NotifyConfigView.Webhook view(NotifyType type, WebhookConfig config) {
        var view = new NotifyConfigView.Webhook();
        view.setWebhookDisplay(WebhookEndpoints.display(type, config.getWebhook()));
        view.setWebhookConfigured(true);
        view.setSecretConfigured(config.getSecret() != null);
        view.setFormat(config.getFormat());
        view.setMembers(config.getMembers());
        view.setMobiles(config.getMobiles());
        view.setRoles(config.getRoles());
        view.setAtAll(config.isAtAll());
        view.setMentionMode(config.getMentionMode());
        view.setKeyword(config.getKeyword());
        view.setDestination(config.getDestination());
        view.setChannelId(config.getChannelId());
        return view;
    }

    private static List<String> ids(List<String> values, String pattern) {
        if (values == null) return List.of();
        if (values.size() > 100
                || values.stream()
                        .anyMatch(
                                v ->
                                        v == null
                                                || !v.matches(pattern)
                                                || "all".equals(v)
                                                || "@all".equals(v))
                || values.stream().distinct().count() != values.size()) throw invalid();
        if (values.stream().mapToInt(String::length).sum() > 1600) throw invalid();
        return List.copyOf(values);
    }

    private static String text(String text, int max) {
        if (text == null || text.isBlank()) return null;
        if (text.length() > max || text.chars().anyMatch(Character::isISOControl)) throw invalid();
        return text.trim();
    }

    private static BusinessException invalid() {
        return BusinessException.localized(
                ResultCode.PARAM_ERROR, "Notification webhook configuration is invalid");
    }
}
