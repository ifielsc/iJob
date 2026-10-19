package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.notify.NotifyConfigListDTO;
import com.ifielsc.ijob.contract.notify.NotifyConfigPageQuery;
import com.ifielsc.ijob.infra.coordination.notification.NotificationOutboxItem;
import com.ifielsc.ijob.infra.coordination.notification.NotificationOutboxStatus;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;
import com.ifielsc.ijob.infra.mapper.coordination.NotificationOutboxMapper;
import com.ifielsc.ijob.infra.mapper.notify.NotifyConfigMapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class NotificationOutboxServiceTest {
    @Test
    void localWaitDefersWithoutRetryAndProviderCooldownControlsRetryDeadline() {
        RecordingMapper mapper = new RecordingMapper();
        NotificationOutboxService service =
                new NotificationOutboxService(mapper, new AppProperties());
        service.enqueue(
                31L,
                NotifyMessage.builder().subject("alarm").content("disk").build(),
                NotifyDispatchContext.system());
        NotificationOutboxItem claimed = service.claim(101L, "worker-a");
        assertTrue(
                service.retry(
                        claimed,
                        new NotifyDispatchResult(
                                NotifyDispatchStatus.DELIVERY_FAILED,
                                NotifyType.DINGTALK,
                                NotifyAuditFailure.RATE_WAIT,
                                null,
                                3100)));
        assertEquals(3100000, mapper.deferredMicros);
        assertEquals(0, mapper.responseWrites);
        assertEquals(null, mapper.failureCode);
        assertTrue(
                service.retry(
                        claimed,
                        new NotifyDispatchResult(
                                NotifyDispatchStatus.DELIVERY_FAILED,
                                NotifyType.DINGTALK,
                                NotifyAuditFailure.RATE_LIMITED,
                                429,
                                60000)));
        assertEquals(60000000, mapper.retryMicros);
        assertEquals(429, mapper.httpStatus);
        assertEquals("RATE_LIMITED", mapper.failureCode);
        assertEquals(1, mapper.responseWrites);
    }

    @Test
    void freezesEmailPresentationButKeepsOtherChannelsAndManualMessagesUnchanged() {
        for (NotifyType type : List.of(NotifyType.EMAIL, NotifyType.DINGTALK)) {
            NotifyConfig config = new NotifyConfig();
            config.setId(31L);
            config.setType(type);
            config.setConfig("protected-snapshot");
            config.setEnabled(true);
            NotificationOutboxService service =
                    new NotificationOutboxService(
                            new RecordingMapper(),
                            new FixedNotifyConfigMapper(config),
                            new AppProperties());
            NotifyMessage message =
                    NotifyMessage.builder()
                            .subject("plain subject")
                            .content("plain text")
                            .emailSubject("named email")
                            .emailContent("<html>email card</html>")
                            .build();
            NotificationOutboxItem item =
                    service.enqueue(31L, message, NotifyDispatchContext.system());
            assertEquals(
                    type == NotifyType.EMAIL ? "named email" : "plain subject", item.getSubject());
            assertEquals(
                    type == NotifyType.EMAIL ? "<html>email card</html>" : "plain text",
                    item.getContent());
            assertEquals("plain text", message.getContent());
        }
        NotifyMessage manual =
                NotifyMessage.builder().subject("钉钉测试aaa").content("钉钉测试aaa").build();
        assertSame(manual, manual.forChannel(NotifyType.EMAIL));
        assertSame(manual, manual.forChannel(NotifyType.DINGTALK));
    }

    @Test
    void duplicateLogicalInstanceNotificationShouldReuseOneOutboxItem() {
        RecordingMapper mapper = new RecordingMapper();
        NotificationOutboxService service =
                new NotificationOutboxService(mapper, new AppProperties());
        NotifyMessage message = NotifyMessage.builder().subject("done").content("success").build();
        NotifyDispatchContext context =
                NotifyDispatchContext.builder()
                        .sourceType(NotifySendSource.INSTANCE)
                        .instanceId(21L)
                        .notifyBindingId(22L)
                        .build();

        NotificationOutboxItem first = service.enqueue(23L, message, context);
        NotificationOutboxItem duplicate = service.enqueue(23L, message, context);

        assertSame(first, duplicate);
        assertEquals(1, mapper.insertedCount);
        assertTrue(first.getDeduplicationKey().startsWith("NOTIFY:"));
    }

    @Test
    void claimRenewAndRetryShouldPreserveLeaseIdentity() {
        RecordingMapper mapper = new RecordingMapper();
        NotificationOutboxService service =
                new NotificationOutboxService(mapper, new AppProperties());
        service.enqueue(
                31L,
                NotifyMessage.builder().subject("alarm").content("disk").build(),
                NotifyDispatchContext.system());

        NotificationOutboxItem claimed = service.claim(101L, "worker-a");

        assertNotNull(claimed.getClaimToken());
        assertEquals("worker-a", claimed.getLeaseOwner());
        assertTrue(service.renew(claimed));
        assertTrue(service.retry(claimed, NotifyDispatchStatus.DELIVERY_FAILED));
        assertEquals("DELIVERY_FAILED", mapper.failureCode);
    }

    @Test
    void enqueueShouldFreezeEncryptedConfigurationWithoutDecryptingIt() {
        RecordingMapper mapper = new RecordingMapper();
        NotifyConfig config = new NotifyConfig();
        config.setId(31L);
        config.setType(NotifyType.DINGTALK);
        config.setConfig("ifjenc:v1:protected-snapshot");
        config.setEnabled(Boolean.TRUE);
        NotificationOutboxService service =
                new NotificationOutboxService(
                        mapper, new FixedNotifyConfigMapper(config), new AppProperties());

        NotificationOutboxItem item =
                service.enqueue(
                        31L,
                        NotifyMessage.builder().subject("alarm").content("disk").build(),
                        NotifyDispatchContext.system());

        assertEquals(NotifyType.DINGTALK, item.getNotifyType());
        assertEquals("ifjenc:v1:protected-snapshot", item.getConfigSnapshot());
        assertTrue(item.getConfigEnabled());
    }

    private static final class RecordingMapper implements NotificationOutboxMapper {
        private long deferredMicros;
        private long retryMicros;
        private int responseWrites;
        private Integer httpStatus;

        @Override
        public int defer(Long id, String owner, String token, long delay) {
            deferredMicros = delay;
            return 1;
        }

        @Override
        public int recordResponse(Long id, String owner, String token, Integer status) {
            responseWrites++;
            httpStatus = status;
            return 1;
        }

        private NotificationOutboxItem stored;
        private int insertedCount;
        private String failureCode;

        @Override
        public int insertIfAbsent(NotificationOutboxItem item) {
            if (stored == null) {
                item.setId(101L);
                item.setStatus(NotificationOutboxStatus.READY);
                item.setAttemptCount(0);
                stored = item;
                insertedCount++;
            }
            return 1;
        }

        @Override
        public NotificationOutboxItem selectByDeduplicationKey(String key) {
            return stored != null && key.equals(stored.getDeduplicationKey()) ? stored : null;
        }

        @Override
        public NotificationOutboxItem selectByClaimToken(String token) {
            return stored != null && token.equals(stored.getClaimToken()) ? stored : null;
        }

        @Override
        public List<Long> selectDispatchableIds(int limit) {
            return stored == null ? List.of() : List.of(stored.getId());
        }

        @Override
        public int claim(Long id, String leaseOwner, String claimToken, long leaseMicros) {
            if (stored == null || !id.equals(stored.getId())) {
                return 0;
            }
            stored.setStatus(NotificationOutboxStatus.CLAIMED);
            stored.setLeaseOwner(leaseOwner);
            stored.setClaimToken(claimToken);
            stored.setLeaseDeadline(LocalDateTime.now().plusSeconds(30));
            return 1;
        }

        @Override
        public int renew(Long id, String leaseOwner, String claimToken, long leaseMicros) {
            return matches(id, leaseOwner, claimToken) ? 1 : 0;
        }

        @Override
        public int complete(Long id, String leaseOwner, String claimToken) {
            return matches(id, leaseOwner, claimToken) ? 1 : 0;
        }

        @Override
        public int retryOrDead(
                Long id,
                String leaseOwner,
                String claimToken,
                long retryDelayMicros,
                String code,
                String summary,
                int terminal) {
            if (!matches(id, leaseOwner, claimToken)) {
                return 0;
            }
            failureCode = code;
            retryMicros = retryDelayMicros;
            return 1;
        }

        @Override
        public int markExpiredExhaustedDead() {
            return 0;
        }

        private boolean matches(Long id, String owner, String token) {
            return stored != null
                    && id.equals(stored.getId())
                    && owner.equals(stored.getLeaseOwner())
                    && token.equals(stored.getClaimToken());
        }
    }

    private static final class FixedNotifyConfigMapper implements NotifyConfigMapper {
        @Override
        public java.util.List<com.ifielsc.ijob.contract.notify.NotifyConfigListDTO>
                selectSummariesByIds(java.util.List<Long> ids) {
            throw new UnsupportedOperationException("Unexpected summary lookup");
        }

        private final NotifyConfig config;

        private FixedNotifyConfigMapper(NotifyConfig config) {
            this.config = config;
        }

        @Override
        public List<NotifyConfigListDTO> selectDtoList(NotifyConfigPageQuery query) {
            return List.of();
        }

        @Override
        public NotifyConfig selectById(Long id) {
            return id.equals(config.getId()) ? config : null;
        }

        @Override
        public List<Long> selectIdsForUpdate(List<Long> ids) {
            return List.of();
        }

        @Override
        public int countById(Long id) {
            return 0;
        }

        @Override
        public int insert(NotifyConfig config) {
            return 0;
        }

        @Override
        public int updateById(NotifyConfig config) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }

        @Override
        public int deleteByIds(List<Long> ids) {
            return 0;
        }
    }
}
