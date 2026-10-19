// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.core.notify.channel.NotifyChannel;
import com.ifielsc.ijob.core.notify.channel.NotifyChannelResult;
import com.ifielsc.ijob.infra.entity.notify.NotifyConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifyDispatcherTest {
    @Test
    void quotaWaitSkipsTransportAndAttemptAuditWhileEmailBypassesQuota() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        configs.config.setType(NotifyType.DINGTALK);
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        NotifyRateLimitService limiter =
                new NotifyRateLimitService(null, null, null) {
                    @Override
                    public long acquire(NotifyConfig config) {
                        assertEquals(NotifyType.DINGTALK, config.getType());
                        return 2500;
                    }
                };
        NotifyChannel dingTalk =
                new NotifyChannel() {
                    @Override
                    public NotifyType type() {
                        return NotifyType.DINGTALK;
                    }

                    @Override
                    public NotifyChannelResult send(NotifyConfig config, NotifyMessage message) {
                        throw new AssertionError("Quota wait must not call the provider");
                    }
                };
        var dispatcher =
                new NotifyDispatcher(
                        configs,
                        List.of(dingTalk, new RecordingNotifyChannel()),
                        recorder,
                        limiter);
        var waiting = dispatcher.dispatch(7L, message(), context());
        assertEquals(NotifyAuditFailure.RATE_WAIT, waiting.failure());
        assertEquals(2500, waiting.retryAfterMillis());
        assertNull(recorder.notifyConfigId);
        assertNull(recorder.completedAuditId);
        configs.config.setType(NotifyType.EMAIL);
        assertEquals(
                NotifyDispatchStatus.DELIVERED,
                dispatcher.dispatch(7L, message(), context()).status());
        assertEquals(901L, recorder.completedAuditId);
    }

    @Test
    void classifiedChannelFailureReachesAuditAndOutboxWithoutProviderText() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        channel.failure =
                new com.ifielsc.ijob.core.notify.channel.NotifyDeliveryException(
                        NotifyAuditFailure.REMOTE_REJECTED,
                        new IllegalStateException("canary-secret"));
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        NotifyDispatchResult result =
                new NotifyDispatcher(configs, List.of(channel), recorder)
                        .dispatch(7L, message(), context());
        assertEquals(NotifyDispatchStatus.DELIVERY_FAILED, result.status());
        assertEquals(NotifyAuditFailure.REMOTE_REJECTED, result.failure());
        assertSame(result.failure(), recorder.failure);
        assertFalse(result.failure().isRetryable());
        assertFalse(result.toString().contains("canary-secret"));
    }

    @Test
    void dispatchShouldDeliverThroughMatchingFakeChannel() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        NotifyDispatcher dispatcher = new NotifyDispatcher(configs, List.of(channel), recorder);
        NotifyMessage message = message();
        NotifyDispatchContext context = context();

        NotifyDispatchResult result = dispatcher.dispatch(7L, message, context);

        assertEquals(NotifyDispatchStatus.DELIVERED, result.status());
        assertEquals(NotifyType.EMAIL, result.type());
        assertSame(configs.config, channel.config);
        assertSame(message, channel.message);
        assertSame(context, recorder.context);
        assertEquals(7L, recorder.notifyConfigId);
        assertEquals(901L, recorder.completedAuditId);
        assertSame(result, recorder.result);
        assertNull(recorder.failure);
    }

    @Test
    void dispatchShouldReturnStablePreconditionResults() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        NotifyDispatcher dispatcher =
                new NotifyDispatcher(configs, List.of(), new RecordingAuditRecorder());

        assertEquals(
                NotifyDispatchStatus.CONFIG_NOT_FOUND,
                dispatcher.dispatch((Long) null, message(), context()).status());
        assertEquals(
                NotifyDispatchStatus.CONFIG_NOT_FOUND,
                dispatcher.dispatch(8L, message(), context()).status());

        configs.config = config(false);
        assertEquals(
                NotifyDispatchStatus.CONFIG_DISABLED,
                dispatcher.dispatch(8L, message(), context()).status());

        configs.config = config(true);
        assertEquals(
                NotifyDispatchStatus.CHANNEL_UNAVAILABLE,
                dispatcher.dispatch(8L, message(), context()).status());
    }

    @Test
    void dispatchShouldConvertChannelFailureToStableResult() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        channel.result = NotifyChannelResult.FAILED;
        NotifyDispatcher dispatcher =
                new NotifyDispatcher(configs, List.of(channel), new RecordingAuditRecorder());

        NotifyDispatchResult result = dispatcher.dispatch(9L, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERY_FAILED, result.status());
        assertEquals(NotifyType.EMAIL, result.type());
    }

    @Test
    void dispatchShouldContainUnexpectedFailureDetails() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        channel.failure = new IllegalStateException("must-not-enter-api-response");
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        NotifyDispatcher dispatcher = new NotifyDispatcher(configs, List.of(channel), recorder);

        NotifyDispatchResult result = dispatcher.dispatch(10L, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERY_FAILED, result.status());
        assertEquals(NotifyAuditFailure.CHANNEL_EXCEPTION, recorder.failure);
        assertFalse(recorder.failure.getSummary().contains("must-not-enter-api-response"));
    }

    @Test
    void dispatchShouldContainRuntimeConfigFailure() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.failure = new IllegalArgumentException("ciphertext-must-not-enter-api-response");
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        NotifyDispatcher dispatcher = new NotifyDispatcher(configs, List.of(), recorder);

        NotifyDispatchResult result = dispatcher.dispatch(11L, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERY_FAILED, result.status());
        assertEquals(NotifyAuditFailure.CONFIG_LOOKUP_FAILED, recorder.failure);
        assertFalse(
                recorder.failure.getSummary().contains("ciphertext-must-not-enter-api-response"));
    }

    @Test
    void dispatchShouldDecryptAndUseTheFrozenConfigurationSnapshot() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        NotifyConfig protectedSnapshot = config(true);
        protectedSnapshot.setConfig("ifjenc:v1:frozen");
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        NotifyDispatcher dispatcher =
                new NotifyDispatcher(configs, List.of(channel), new RecordingAuditRecorder());

        NotifyDispatchResult result = dispatcher.dispatch(protectedSnapshot, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERED, result.status());
        assertSame(protectedSnapshot, configs.protectedSnapshot);
        assertSame(configs.config, channel.config);
    }

    @Test
    void auditFailuresShouldNotChangeDeliveryResult() {
        StubNotifyConfigService configs = new StubNotifyConfigService();
        configs.config = config(true);
        RecordingNotifyChannel channel = new RecordingNotifyChannel();
        RecordingAuditRecorder recorder = new RecordingAuditRecorder();
        recorder.startFailure = new IllegalStateException("database-secret");
        NotifyDispatcher dispatcher = new NotifyDispatcher(configs, List.of(channel), recorder);

        NotifyDispatchResult startFailureResult = dispatcher.dispatch(12L, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERED, startFailureResult.status());
        assertNull(recorder.completedAuditId);

        recorder.startFailure = null;
        recorder.completeFailure = new IllegalStateException("database-secret");
        NotifyDispatchResult completeFailureResult = dispatcher.dispatch(13L, message(), context());

        assertEquals(NotifyDispatchStatus.DELIVERED, completeFailureResult.status());
    }

    private static NotifyConfig config(boolean enabled) {
        NotifyConfig config = new NotifyConfig();
        config.setId(7L);
        config.setType(NotifyType.EMAIL);
        config.setEnabled(enabled);
        config.setConfig("runtime-only-plaintext");
        return config;
    }

    private static NotifyMessage message() {
        return NotifyMessage.builder().subject("test").content("content").build();
    }

    private static NotifyDispatchContext context() {
        return NotifyDispatchContext.builder()
                .sourceType(NotifySendSource.TEST_SEND)
                .operatorId("operator-1")
                .build();
    }

    private static final class StubNotifyConfigService extends NotifyConfigService {
        private NotifyConfig config;
        private NotifyConfig protectedSnapshot;
        private RuntimeException failure;

        private StubNotifyConfigService() {
            super(null, null, null, null);
        }

        @Override
        public NotifyConfig getNotifyConfig(Long id) {
            if (failure != null) {
                throw failure;
            }
            return config;
        }

        @Override
        public NotifyConfig decryptSnapshot(NotifyConfig snapshot) {
            protectedSnapshot = snapshot;
            if (failure != null) {
                throw failure;
            }
            return config;
        }
    }

    private static final class RecordingNotifyChannel implements NotifyChannel {
        private NotifyChannelResult result = NotifyChannelResult.DELIVERED;
        private RuntimeException failure;
        private NotifyConfig config;
        private NotifyMessage message;

        @Override
        public NotifyType type() {
            return NotifyType.EMAIL;
        }

        @Override
        public NotifyChannelResult send(NotifyConfig sentConfig, NotifyMessage sentMessage) {
            if (failure != null) {
                throw failure;
            }
            config = sentConfig;
            message = sentMessage;
            return result;
        }
    }

    private static final class RecordingAuditRecorder extends NotifyAuditRecorder {
        private RuntimeException startFailure;
        private RuntimeException completeFailure;
        private NotifyDispatchContext context;
        private Long notifyConfigId;
        private Long completedAuditId;
        private NotifyDispatchResult result;
        private NotifyAuditFailure failure;

        private RecordingAuditRecorder() {
            super(null);
        }

        @Override
        public Long start(NotifyDispatchContext dispatchContext, Long configId) {
            if (startFailure != null) {
                throw startFailure;
            }
            context = dispatchContext;
            notifyConfigId = configId;
            return 901L;
        }

        @Override
        public void complete(
                Long auditId,
                NotifyDispatchResult dispatchResult,
                NotifyAuditFailure auditFailure,
                long durationMs) {
            if (completeFailure != null) {
                throw completeFailure;
            }
            completedAuditId = auditId;
            result = dispatchResult;
            failure = auditFailure;
        }
    }
}
