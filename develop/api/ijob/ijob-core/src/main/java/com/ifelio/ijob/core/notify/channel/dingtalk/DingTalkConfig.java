package com.ifelio.ijob.core.notify.channel.dingtalk;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString(exclude = {"webhook", "secret", "atMobiles"})
public class DingTalkConfig {
    private String webhook;

    /** Webhook signing secret. */
    private String secret;

    private String msgType = "markdown";

    private Boolean atAll = false;

    private List<String> atMobiles;
}
