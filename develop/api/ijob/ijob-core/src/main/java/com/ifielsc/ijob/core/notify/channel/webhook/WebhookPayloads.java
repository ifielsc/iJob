// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify.channel.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.NotifyMessage;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Version-one provider payloads. Content is rendered once, before enqueue; signatures are not. */
@Component
public class WebhookPayloads {
    private final ObjectMapper json;

    public WebhookPayloads(ObjectMapper json) {
        this.json = json;
    }

    public NotifyMessage freeze(NotifyType type, String config, NotifyMessage message) {
        if (message.getContentVersion() != 0 && message.getContentVersion() != 1)
            throw new IllegalArgumentException("Unsupported notification content version");
        if (type == null
                || type == NotifyType.EMAIL
                || type == NotifyType.DINGTALK
                || message.getContentVersion() == 1) return message;
        try {
            var c = json.readValue(config, WebhookConfig.class);
            return NotifyMessage.builder()
                    .title(message.getTitle())
                    .subject(message.getSubject())
                    .content(json.writeValueAsString(body(type, c, message)))
                    .contentVersion(1)
                    .build();
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Notification payload cannot be rendered");
        }
    }

    public Map<String, Object> body(NotifyType type, WebhookConfig c, NotifyMessage m) {
        String subject = limit(m.getSubject(), 220);
        String content = m.getContent() == null ? "" : m.getContent();
        return switch (type) {
            case WECOM -> {
                if ("text".equals(c.getFormat())) {
                    List<String> members = new ArrayList<>(c.getMembers());
                    if (c.isAtAll()) members.add("@all");
                    yield Map.of(
                            "msgtype",
                            "text",
                            "text",
                            Map.of(
                                    "content",
                                    limit(plain(content), 2000),
                                    "mentioned_list",
                                    members,
                                    "mentioned_mobile_list",
                                    c.getMobiles()));
                }
                String mentions =
                        c.getMembers().stream()
                                .map(id -> "<@" + id + ">")
                                .collect(java.util.stream.Collectors.joining(" "));
                yield Map.of(
                        "msgtype",
                        "markdown",
                        "markdown",
                        Map.of(
                                "content",
                                limit(
                                                content.replace("<", "&lt;").replace(">", "&gt;"),
                                                Math.max(500, 3999 - mentions.length()))
                                        + "\n"
                                        + mentions));
            }
            case FEISHU -> {
                String mentions =
                        c.isAtAll()
                                ? "<at id=all></at>"
                                : c.getMembers().stream()
                                        .map(id -> "<at id=" + id + "></at>")
                                        .collect(java.util.stream.Collectors.joining(" "));
                String safe = content.replace("<", "&lt;").replace(">", "&gt;");
                String prefix = c.getKeyword() == null ? "" : c.getKeyword() + " · ";
                yield Map.of(
                        "msg_type",
                        "interactive",
                        "card",
                        Map.of(
                                "header",
                                Map.of(
                                        "template",
                                        "blue",
                                        "title",
                                        Map.of("tag", "plain_text", "content", prefix + subject)),
                                "elements",
                                List.of(
                                        Map.of(
                                                "tag",
                                                "div",
                                                "text",
                                                Map.of(
                                                        "tag",
                                                        "lark_md",
                                                        "content",
                                                        limit(safe, 12000) + "\n" + mentions)))));
            }
            case SLACK -> {
                String mention =
                        switch (c.getMentionMode()) {
                            case "here" -> "<!here>";
                            case "channel" -> "<!channel>";
                            case "members" ->
                                    c.getMembers().stream()
                                            .map(id -> "<@" + id + ">")
                                            .collect(java.util.stream.Collectors.joining(" "));
                            default -> "";
                        };
                List<Map<String, Object>> blocks = new ArrayList<>();
                blocks.add(
                        Map.of(
                                "type",
                                "header",
                                "text",
                                Map.of("type", "plain_text", "text", limit(subject, 140))));
                String text = limit(plain(content), 19000);
                for (int offset = 0; offset < text.length() && blocks.size() < 9; ) {
                    int end = Math.min(offset + 2500, text.length());
                    if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)))
                        end--;
                    blocks.add(
                            Map.of(
                                    "type",
                                    "section",
                                    "text",
                                    Map.of(
                                            "type",
                                            "plain_text",
                                            "text",
                                            text.substring(offset, end))));
                    offset = end;
                }
                if (!mention.isEmpty())
                    blocks.add(
                            Map.of(
                                    "type",
                                    "section",
                                    "text",
                                    Map.of("type", "mrkdwn", "text", mention, "verbatim", true)));
                yield Map.of(
                        "text",
                        subject.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"),
                        "blocks",
                        blocks,
                        "unfurl_links",
                        false,
                        "unfurl_media",
                        false);
            }
            case DISCORD -> {
                String mentions =
                        c.getMembers().stream()
                                        .map(id -> "<@" + id + ">")
                                        .collect(java.util.stream.Collectors.joining(" "))
                                + " "
                                + c.getRoles().stream()
                                        .map(id -> "<@&" + id + ">")
                                        .collect(java.util.stream.Collectors.joining(" "));
                if (mentions.length() > 2000)
                    throw new IllegalArgumentException("Too many notification mentions");
                yield Map.of(
                        "embeds",
                        List.of(Map.of("title", subject, "description", limit(content, 3900))),
                        "content",
                        mentions.trim(),
                        "allowed_mentions",
                        Map.of(
                                "parse",
                                List.of(),
                                "users",
                                c.getMembers(),
                                "roles",
                                c.getRoles(),
                                "replied_user",
                                false));
            }
            default -> throw new IllegalArgumentException("Unsupported webhook type");
        };
    }

    private static String plain(String value) {
        return value.replaceAll("(?m)^#{1,6} +", "")
                .replace("**", "")
                .replaceAll("\\\\([*_`>])", "$1");
    }

    /** UTF-8 budget with a retained tail so identities and run links survive truncation. */
    public static String limit(String value, int bytes) {
        if (value == null) return "";
        if (value.getBytes(StandardCharsets.UTF_8).length <= bytes) return value;
        int[] points = value.codePoints().toArray();
        StringBuilder head = new StringBuilder(), tail = new StringBuilder();
        int used = 0, tailBudget = Math.min(bytes / 4, 700);
        for (int i = points.length - 1; i >= 0; i--) {
            String cp = new String(Character.toChars(points[i]));
            if (used + cp.getBytes(StandardCharsets.UTF_8).length > tailBudget) break;
            tail.insert(0, cp);
            used += cp.getBytes(StandardCharsets.UTF_8).length;
        }
        used += 9;
        for (int cp : points) {
            String s = new String(Character.toChars(cp));
            if (used + s.getBytes(StandardCharsets.UTF_8).length > bytes) break;
            head.append(s);
            used += s.getBytes(StandardCharsets.UTF_8).length;
        }
        return head + "\n[…]\n" + tail;
    }
}
