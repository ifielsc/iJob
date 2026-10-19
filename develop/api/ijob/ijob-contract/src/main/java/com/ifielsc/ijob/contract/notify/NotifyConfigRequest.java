package com.ifielsc.ijob.contract.notify;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

public final class NotifyConfigRequest {
    private NotifyConfigRequest() {}

    @Getter
    @Setter
    public static class Webhook {
        private Boolean preserveExisting;
        private String webhook;
        private String secret;
        private Boolean clearSecret;
        private String format;
        private List<String> members;
        private List<String> mobiles;
        private List<String> roles;
        private Boolean atAll;
        private String mentionMode;
        private String keyword;
        private String destination;
        private String channelId;
    }

    @Getter
    @Setter
    @ToString(exclude = {"from", "to", "cc"})
    public static class Email {
        private Long mailChannelId;
        private Boolean preserveExisting;
        private String from;
        private List<String> to;
        private List<String> cc;
    }

    @Getter
    @Setter
    @ToString(exclude = {"webhook", "secret", "atMobiles"})
    public static class DingTalk {
        private Boolean preserveExisting;
        private String webhook;
        private String secret;
        private Boolean clearSecret;
        private Boolean atAll;
        private List<String> atMobiles;
    }
}
