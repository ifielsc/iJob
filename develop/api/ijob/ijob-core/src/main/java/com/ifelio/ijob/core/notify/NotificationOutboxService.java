package com.ifelio.ijob.core.notify;

import com.ifelio.ijob.common.config.AppProperties;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.infra.coordination.notification.NotificationOutboxItem;
import com.ifelio.ijob.infra.entity.notify.NotifyConfig;
import com.ifelio.ijob.infra.mapper.coordination.NotificationOutboxMapper;
import com.ifelio.ijob.infra.mapper.notify.NotifyConfigMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationOutboxService {
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 10000;

    private final NotificationOutboxMapper mapper;
    private final NotifyConfigMapper notifyConfigMapper;
    private final AppProperties appProperties;
    private com.ifelio.ijob.core.notify.channel.webhook.WebhookPayloads payloads;
    private NotifyConfigService configs;

    @Autowired
    public NotificationOutboxService(
            NotificationOutboxMapper mapper,
            NotifyConfigMapper notifyConfigMapper,
            AppProperties appProperties,
            com.ifelio.ijob.core.notify.channel.webhook.WebhookPayloads payloads,
            NotifyConfigService configs) {
        this(mapper, notifyConfigMapper, appProperties);
        this.payloads = payloads;
        this.configs = configs;
    }

    public NotificationOutboxService(
            NotificationOutboxMapper mapper,
            NotifyConfigMapper notifyConfigMapper,
            AppProperties appProperties) {
        this.mapper = mapper;
        this.notifyConfigMapper = notifyConfigMapper;
        this.appProperties = appProperties;
    }

    NotificationOutboxService(NotificationOutboxMapper mapper, AppProperties appProperties) {
        this(mapper, null, appProperties);
    }

    @Transactional
    public NotificationOutboxItem enqueue(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        NotifyConfig config =
                notifyConfigMapper == null || notifyConfigId == null
                        ? null
                        : notifyConfigMapper.selectById(notifyConfigId);
        if (message != null) message = message.forChannel(config == null ? null : config.getType());
        if (message != null
                && config != null
                && config.getType() != com.ifelio.ijob.contract.enums.NotifyType.EMAIL
                && config.getType() != com.ifelio.ijob.contract.enums.NotifyType.DINGTALK
                && payloads != null)
            message =
                    payloads.freeze(
                            config.getType(), configs.decryptSnapshot(config).getConfig(), message);
        validate(notifyConfigId, message);
        NotifyDispatchContext resolved = context == null ? NotifyDispatchContext.system() : context;
        NotificationOutboxItem requested = new NotificationOutboxItem();
        requested.setDeduplicationKey(deduplicationKey(notifyConfigId, message, resolved));
        requested.setNotifyConfigId(notifyConfigId);
        snapshotConfig(requested, config);
        requested.setTitle(trimToNull(message.getTitle()));
        requested.setSubject(message.getSubject().trim());
        requested.setContent(message.getContent());
        requested.setContentVersion(message.getContentVersion());
        requested.setSourceType(
                resolved.getSourceType() == null
                        ? NotifySendSource.SYSTEM
                        : resolved.getSourceType());
        requested.setCorrelationId(safeText(resolved.getCorrelationId(), 64));
        requested.setOperatorId(safeText(resolved.getOperatorId(), 128));
        requested.setTargetType(resolved.getTargetType());
        requested.setTargetId(positiveOrNull(resolved.getTargetId()));
        requested.setInstanceId(positiveOrNull(resolved.getInstanceId()));
        requested.setNotifyBindingId(positiveOrNull(resolved.getNotifyBindingId()));
        requested.setMaxAttempts(appProperties.getNotification().getOutbox().getMaxAttempts());

        mapper.insertIfAbsent(requested);
        NotificationOutboxItem persisted =
                mapper.selectByDeduplicationKey(requested.getDeduplicationKey());
        if (persisted == null || persisted.getId() == null) {
            throw BusinessException.localized(
                    ResultCode.SYSTEM_ERROR, "Notification outbox enqueue failed");
        }
        requireEquivalent(requested, persisted);
        return persisted;
    }

    public List<Long> listDispatchableIds() {
        mapper.markExpiredExhaustedDead();
        return List.copyOf(
                mapper.selectDispatchableIds(
                        appProperties.getNotification().getOutbox().getScanBatchSize()));
    }

    @Transactional
    public NotificationOutboxItem claim(Long id, String leaseOwner) {
        String token = UUID.randomUUID().toString();
        if (mapper.claim(id, leaseOwner, token, micros(leaseDuration())) != 1) {
            return null;
        }
        NotificationOutboxItem claimed = mapper.selectByClaimToken(token);
        if (claimed == null || !id.equals(claimed.getId())) {
            throw new IllegalStateException("Claimed notification cannot be read back");
        }
        return claimed;
    }

    @Transactional
    public boolean renew(NotificationOutboxItem item) {
        return mapper.renew(
                        item.getId(),
                        item.getLeaseOwner(),
                        item.getClaimToken(),
                        micros(leaseDuration()))
                == 1;
    }

    @Transactional
    public boolean complete(NotificationOutboxItem item) {
        return mapper.complete(item.getId(), item.getLeaseOwner(), item.getClaimToken()) == 1;
    }

    @Transactional
    public boolean retry(NotificationOutboxItem item, NotifyDispatchStatus status) {
        String code = status == null ? "UNKNOWN" : status.name();
        return mapper.retryOrDead(
                        item.getId(),
                        item.getLeaseOwner(),
                        item.getClaimToken(),
                        micros(appProperties.getNotification().getOutbox().getRetryDelay()),
                        code,
                        "Notification delivery did not complete",
                        0)
                == 1;
    }

    @Transactional
    public boolean retry(NotificationOutboxItem item, NotifyDispatchResult result) {
        NotifyAuditFailure failure = result.failure();
        if (failure == NotifyAuditFailure.RATE_WAIT)
            return mapper.defer(
                            item.getId(),
                            item.getLeaseOwner(),
                            item.getClaimToken(),
                            Math.max(1000, result.retryAfterMillis()) * 1000)
                    == 1;
        if (mapper.recordResponse(
                        item.getId(),
                        item.getLeaseOwner(),
                        item.getClaimToken(),
                        result.httpStatus())
                != 1) return false;
        if (failure == null) return retry(item, result.status());
        return mapper.retryOrDead(
                        item.getId(),
                        item.getLeaseOwner(),
                        item.getClaimToken(),
                        Math.max(
                                micros(appProperties.getNotification().getOutbox().getRetryDelay()),
                                result.retryAfterMillis() * 1000),
                        failure.name(),
                        failure.getSummary(),
                        failure.isRetryable() ? 0 : 1)
                == 1;
    }

    private Duration leaseDuration() {
        return appProperties.getNotification().getOutbox().getLeaseDuration();
    }

    private long micros(Duration duration) {
        try {
            return Math.multiplyExact(duration.toMillis(), 1000L);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Notification outbox duration is too large", exception);
        }
    }

    private void validate(Long notifyConfigId, NotifyMessage message) {
        if (notifyConfigId == null || notifyConfigId <= 0) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Notify config ID is invalid");
        }
        if (message == null
                || message.getSubject() == null
                || message.getSubject().isBlank()
                || message.getSubject().trim().length() > MAX_SUBJECT_LENGTH
                || message.getContent() == null
                || message.getContent().isBlank()
                || message.getContent().length()
                        > (message.getContentVersion() == 1 ? 32000 : MAX_CONTENT_LENGTH)
                || (message.getTitle() != null
                        && message.getTitle().length() > MAX_SUBJECT_LENGTH)) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Notification message is invalid");
        }
    }

    private String deduplicationKey(
            Long notifyConfigId, NotifyMessage message, NotifyDispatchContext context) {
        String logicalIdentity;
        if (context.getInstanceId() != null && context.getNotifyBindingId() != null) {
            logicalIdentity =
                    "INSTANCE:"
                            + context.getInstanceId()
                            + ":BINDING:"
                            + context.getNotifyBindingId();
        } else {
            logicalIdentity =
                    String.join(
                            "|",
                            String.valueOf(context.getSourceType()),
                            String.valueOf(context.getInstanceId()),
                            String.valueOf(context.getCorrelationId()),
                            String.valueOf(notifyConfigId),
                            message.getSubject(),
                            message.getContent());
        }
        return "NOTIFY:" + digest(logicalIdentity);
    }

    private String digest(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireEquivalent(
            NotificationOutboxItem requested, NotificationOutboxItem persisted) {
        if (!Objects.equals(requested.getNotifyConfigId(), persisted.getNotifyConfigId())
                || !Objects.equals(requested.getTitle(), persisted.getTitle())
                || !Objects.equals(requested.getSubject(), persisted.getSubject())
                || !Objects.equals(requested.getContent(), persisted.getContent())
                || requested.getContentVersion() != persisted.getContentVersion()
                || requested.getSourceType() != persisted.getSourceType()
                || !Objects.equals(requested.getInstanceId(), persisted.getInstanceId())
                || !Objects.equals(
                        requested.getNotifyBindingId(), persisted.getNotifyBindingId())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT,
                    "Notification deduplication key was reused with different content");
        }
    }

    private void snapshotConfig(NotificationOutboxItem requested, NotifyConfig config) {
        if (config == null
                || config.getType() == null
                || config.getConfig() == null
                || config.getEnabled() == null) {
            return;
        }
        requested.setNotifyType(config.getType());
        requested.setConfigSnapshot(config.getConfig());
        requested.setConfigEnabled(config.getEnabled());
    }

    private String safeText(String value, int maxLength) {
        String normalized = trimToNull(value);
        return normalized == null || normalized.length() > maxLength ? null : normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long positiveOrNull(Long value) {
        return value != null && value > 0 ? value : null;
    }
}
