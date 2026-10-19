package com.ifielsc.ijob.core.notify.channel.webhook;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Secrets are encrypted at rest and intentionally excluded from diagnostic representations. */
@Getter
@Setter
public class WebhookConfig {
    private String webhook;
    private String secret;
    private String format;
    private List<String> members = List.of();
    private List<String> mobiles = List.of();
    private List<String> roles = List.of();
    private boolean atAll;
    private String mentionMode = "none";
    private String keyword;
    private String destination;
    private String channelId;
}
