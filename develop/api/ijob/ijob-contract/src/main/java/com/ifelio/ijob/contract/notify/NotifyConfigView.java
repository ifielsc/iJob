package com.ifelio.ijob.contract.notify;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public abstract class NotifyConfigView {
    @Getter
    @Setter
    public static class Webhook extends NotifyConfigView {
        private String webhookDisplay;
        private boolean webhookConfigured;
        private boolean secretConfigured;
        private String format;
        private List<String> members;
        private List<String> mobiles;
        private List<String> roles;
        private boolean atAll;
        private String mentionMode;
        private String keyword;
        private String destination;
        private String channelId;
    }

    @Getter
    @Setter
    public static class Email extends NotifyConfigView {
        private Long mailChannelId;
        private String mailChannelName;
        private String from;
        private List<String> to;
        private List<String> cc;
        private boolean fromConfigured;
        private int toCount;
        private int ccCount;
    }

    @Getter
    @Setter
    public static class DingTalk extends NotifyConfigView {
        private String webhookDisplay;
        private List<String> atMobiles;
        private boolean webhookConfigured;
        private boolean secretConfigured;
        private String msgType;
        private boolean atAll;
        private int atMobileCount;
    }
}
