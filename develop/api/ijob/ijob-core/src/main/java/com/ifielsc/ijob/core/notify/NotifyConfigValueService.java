package com.ifielsc.ijob.core.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigUpsertRequest;
import com.ifielsc.ijob.contract.notify.NotifyConfigView;
import com.ifielsc.ijob.core.notify.channel.dingtalk.DingTalkConfig;
import com.ifielsc.ijob.core.notify.channel.email.EmailConfig;
import com.ifielsc.ijob.core.notify.channel.email.MailChannelService;
import com.ifielsc.ijob.core.notify.channel.webhook.WebhookConfig;
import com.ifielsc.ijob.core.notify.channel.webhook.WebhookConfigValues;
import com.ifielsc.ijob.core.notify.security.NotifyConfigCipher;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class NotifyConfigValueService {
    private static final int MAX_ENDPOINT_LENGTH = 2048;
    private static final int MAX_SECRET_LENGTH = 4096;
    private static final int MAX_ADDRESS_LENGTH = 320;
    private static final int MAX_RECIPIENTS = 100;
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\\+?[0-9]{6,20}");

    private final NotifyConfigCipher cipher;
    private final ObjectMapper objectMapper;
    private final MailChannelService mailChannels;

    @org.springframework.beans.factory.annotation.Autowired
    public NotifyConfigValueService(
            NotifyConfigCipher cipher, ObjectMapper objectMapper, MailChannelService mailChannels) {
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.mailChannels = mailChannels;
    }

    public String protect(NotifyConfigUpsertRequest request, NotifyConfig existing) {
        requireMatchingConfig(request);
        String plaintext =
                switch (request.getType()) {
                    case WECOM, FEISHU, SLACK, DISCORD ->
                            write(
                                    WebhookConfigValues.build(
                                            request.getType(),
                                            request.getWebhookConfig(),
                                            previous(
                                                    existing,
                                                    request.getType(),
                                                    WebhookConfig.class)));
                    case EMAIL ->
                            write(
                                    buildEmail(
                                            request.getEmailConfig(),
                                            previous(
                                                    existing,
                                                    NotifyType.EMAIL,
                                                    EmailConfig.class)));
                    case DINGTALK ->
                            write(
                                    buildDingTalk(
                                            request.getDingTalkConfig(),
                                            previous(
                                                    existing,
                                                    NotifyType.DINGTALK,
                                                    DingTalkConfig.class)));
                };
        return cipher.encrypt(plaintext);
    }

    public NotifyConfigView view(NotifyConfig config) {
        return switch (config.getType()) {
            case WECOM, FEISHU, SLACK, DISCORD ->
                    WebhookConfigValues.view(config.getType(), read(config, WebhookConfig.class));
            case EMAIL -> emailView(read(config, EmailConfig.class));
            case DINGTALK -> dingTalkView(read(config, DingTalkConfig.class));
        };
    }

    public String decryptForRuntime(NotifyConfig config) {
        requireUsableConfig(config);
        return cipher.decrypt(config.getConfig());
    }

    private EmailConfig buildEmail(NotifyConfigRequest.Email request, EmailConfig previous) {
        boolean retain = Boolean.TRUE.equals(request.getPreserveExisting()) && previous != null;
        Long channelId =
                request.getMailChannelId() != null
                        ? request.getMailChannelId()
                        : retain ? previous.getMailChannelId() : null;
        if (channelId == null) throw paramError("Select a mail channel before sending");
        String from = normalizeAddress(mailChannels.getEnabledDetail(channelId).getFrom(), "from");
        List<String> to =
                normalizeAddresses(
                        retain && request.getTo() == null ? previous.getTo() : request.getTo(),
                        "to",
                        true);
        List<String> cc =
                normalizeAddresses(
                        retain && request.getCc() == null ? previous.getCc() : request.getCc(),
                        "cc",
                        false);
        rejectRecipientOverlap(to, cc);

        EmailConfig config = new EmailConfig();
        config.setMailChannelId(channelId);
        config.setFrom(from);
        config.setTo(to);
        config.setCc(cc);
        return config;
    }

    private DingTalkConfig buildDingTalk(
            NotifyConfigRequest.DingTalk request, DingTalkConfig previous) {
        String webhook = mergeWebhook(request.getWebhook(), previous);
        String secret = mergeSecret(request, previous);
        List<String> mobiles =
                normalizeMobiles(
                        Boolean.TRUE.equals(request.getPreserveExisting())
                                        && previous != null
                                        && request.getAtMobiles() == null
                                ? previous.getAtMobiles()
                                : request.getAtMobiles());

        DingTalkConfig config = new DingTalkConfig();
        config.setWebhook(webhook);
        config.setSecret(secret);
        config.setMsgType("markdown");
        config.setAtAll(
                Boolean.TRUE.equals(
                        Boolean.TRUE.equals(request.getPreserveExisting())
                                        && previous != null
                                        && request.getAtAll() == null
                                ? previous.getAtAll()
                                : request.getAtAll()));
        config.setAtMobiles(mobiles);
        return config;
    }

    private String mergeWebhook(String requested, DingTalkConfig previous) {
        if (requested == null) {
            if (previous == null || isBlank(previous.getWebhook())) {
                throw paramError("DingTalk webhook is required");
            }
            return previous.getWebhook();
        }
        String webhook = requested.trim();
        if (webhook.isEmpty()) {
            throw paramError("DingTalk webhook is required");
        }
        validateHttpsEndpoint(webhook);
        return webhook;
    }

    private String mergeSecret(NotifyConfigRequest.DingTalk request, DingTalkConfig previous) {
        String requested = request.getSecret();
        boolean clear = Boolean.TRUE.equals(request.getClearSecret());
        if (clear && requested != null && !requested.isBlank()) {
            throw paramError("secret and clearSecret cannot be set together");
        }
        if (clear) {
            return null;
        }
        if (requested == null) {
            return previous == null ? null : previous.getSecret();
        }
        if (requested.isBlank()) {
            throw paramError("DingTalk secret cannot be blank");
        }
        requireMax(requested, MAX_SECRET_LENGTH, "DingTalk secret is too long");
        return requested;
    }

    private List<String> normalizeAddresses(
            List<String> addresses, String field, boolean required) {
        if (addresses == null || addresses.isEmpty()) {
            if (required) {
                throw paramError(field + " must contain a recipient");
            }
            return List.of();
        }
        if (addresses.size() > MAX_RECIPIENTS) {
            throw paramError(field + " cannot contain more than 100 entries");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String address : addresses) {
            String normalized = normalizeAddress(address, field);
            String key = normalized.toLowerCase(Locale.ROOT);
            if (unique.putIfAbsent(key, normalized) != null) {
                throw paramError(field + " contains a duplicate address");
            }
        }
        return List.copyOf(unique.values());
    }

    private String normalizeAddress(String value, String field) {
        if (isBlank(value)) {
            throw paramError(field + " email address is required");
        }
        String address = value.trim();
        requireMax(address, MAX_ADDRESS_LENGTH, field + " email address is too long");
        try {
            InternetAddress parsed = new InternetAddress(address, true);
            parsed.validate();
        } catch (AddressException exception) {
            throw paramError(field + " contains an invalid email address");
        }
        return address;
    }

    private void rejectRecipientOverlap(List<String> to, List<String> cc) {
        var toKeys =
                to.stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toSet());
        boolean overlap =
                cc.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(toKeys::contains);
        if (overlap) {
            throw paramError("to and cc cannot contain the same recipient");
        }
    }

    private List<String> normalizeMobiles(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_RECIPIENTS) {
            throw paramError("atMobiles cannot contain more than 100 entries");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : values) {
            String mobile = value == null ? "" : value.trim();
            if (!MOBILE_PATTERN.matcher(mobile).matches()) {
                throw paramError("atMobiles contains an invalid mobile number");
            }
            if (unique.putIfAbsent(mobile, mobile) != null) {
                throw paramError("atMobiles contains a duplicate number");
            }
        }
        return List.copyOf(unique.values());
    }

    private void validateHttpsEndpoint(String value) {
        requireMax(value, MAX_ENDPOINT_LENGTH, "DingTalk webhook is too long");
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || isBlank(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw paramError("DingTalk webhook must be a safe HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw paramError("DingTalk webhook must be a valid HTTPS URL");
        }
    }

    private void requireMatchingConfig(NotifyConfigUpsertRequest request) {
        if (request == null || request.getType() == null) {
            throw paramError("notify type is required");
        }
        int configured =
                (request.getEmailConfig() == null ? 0 : 1)
                        + (request.getDingTalkConfig() == null ? 0 : 1)
                        + (request.getWebhookConfig() == null ? 0 : 1);
        boolean matching =
                switch (request.getType()) {
                    case WECOM, FEISHU, SLACK, DISCORD -> request.getWebhookConfig() != null;
                    case EMAIL -> request.getEmailConfig() != null;
                    case DINGTALK -> request.getDingTalkConfig() != null;
                };
        if (configured != 1 || !matching) {
            throw paramError("exactly one matching notify config is required");
        }
    }

    private <T> T previous(NotifyConfig existing, NotifyType expectedType, Class<T> type) {
        if (existing == null || existing.getType() != expectedType) {
            return null;
        }
        return read(existing, type);
    }

    private <T> T read(NotifyConfig config, Class<T> type) {
        requireUsableConfig(config);
        try {
            return objectMapper.readValue(cipher.decrypt(config.getConfig()), type);
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "notify config payload is invalid");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "notify config serialization failed");
        }
    }

    private NotifyConfigView.Email emailView(EmailConfig config) {
        NotifyConfigView.Email view = new NotifyConfigView.Email();
        view.setMailChannelId(config.getMailChannelId());
        view.setFrom(config.getFrom());
        if (config.getMailChannelId() != null && mailChannels != null) {
            var channel = mailChannels.getDetail(config.getMailChannelId());
            view.setMailChannelName(channel.getName());
            view.setFrom(channel.getFrom());
        }
        view.setTo(config.getTo() == null ? List.of() : List.copyOf(config.getTo()));
        view.setCc(config.getCc() == null ? List.of() : List.copyOf(config.getCc()));
        view.setFromConfigured(!isBlank(view.getFrom()));
        view.setToCount(size(config.getTo()));
        view.setCcCount(size(config.getCc()));
        return view;
    }

    private NotifyConfigView.DingTalk dingTalkView(DingTalkConfig config) {
        NotifyConfigView.DingTalk view = new NotifyConfigView.DingTalk();
        view.setWebhookDisplay(maskWebhook(config.getWebhook()));
        view.setAtMobiles(
                config.getAtMobiles() == null ? List.of() : List.copyOf(config.getAtMobiles()));
        view.setWebhookConfigured(!isBlank(config.getWebhook()));
        view.setSecretConfigured(!isBlank(config.getSecret()));
        view.setMsgType(config.getMsgType());
        view.setAtAll(Boolean.TRUE.equals(config.getAtAll()));
        view.setAtMobileCount(size(config.getAtMobiles()));
        return view;
    }

    private String maskWebhook(String webhook) {
        if (isBlank(webhook)) {
            return null;
        }
        try {
            URI uri = new URI(webhook);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null) {
                return null;
            }
            // Hide the entire query, including unknown or encoded credential parameters.
            return uri.getScheme()
                    + "://"
                    + uri.getRawAuthority()
                    + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?••••");
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private void requireUsableConfig(NotifyConfig config) {
        if (config == null || config.getType() == null || isBlank(config.getConfig())) {
            throw BusinessException.localized(ResultCode.CONFLICT, "notify config is not usable");
        }
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private void requireMax(String value, int max, String message) {
        if (value != null && value.length() > max) {
            throw paramError(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }
}
