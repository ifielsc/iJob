package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ifielsc.ijob.contract.enums.NotifySendResult;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.NotifyType;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.infra.entity.notify.NotifySendLog;
import com.ifielsc.ijob.infra.mapper.notify.NotifySendLogMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

class NotifyAuditRecorderTest {
    @Test
    void startShouldPersistOnlySafeContextSnapshot() {
        RecordingMapper mapper = new RecordingMapper();
        NotifyAuditRecorder recorder = new NotifyAuditRecorder(mapper);
        NotifyDispatchContext context =
                NotifyDispatchContext.builder()
                        .sourceType(NotifySendSource.INSTANCE)
                        .correlationId("correlation-secret")
                        .operatorId("operator-secret")
                        .targetType(TargetType.TASK)
                        .targetId(17L)
                        .instanceId(18L)
                        .notifyBindingId(19L)
                        .build();

        Long auditId = recorder.start(context, 20L);

        assertEquals(7001L, auditId);
        assertEquals(NotifySendSource.INSTANCE, mapper.started.getSourceType());
        assertEquals("correlation-secret", mapper.started.getCorrelationId());
        assertEquals("operator-secret", mapper.started.getOperatorId());
        assertEquals(TargetType.TASK, mapper.started.getTargetType());
        assertEquals(17L, mapper.started.getTargetId());
        assertEquals(18L, mapper.started.getInstanceId());
        assertEquals(19L, mapper.started.getNotifyBindingId());
        assertEquals(20L, mapper.started.getNotifyConfigId());
        assertEquals(NotifySendResult.STARTED, mapper.started.getResult());
        assertNotNull(mapper.started.getCreateTime());
        assertNotNull(mapper.started.getUpdateTime());
        assertFalse(context.toString().contains("correlation-secret"));
        assertFalse(context.toString().contains("operator-secret"));
    }

    @Test
    void completeShouldUseFixedFailureWhitelist() {
        RecordingMapper mapper = new RecordingMapper();
        NotifyAuditRecorder recorder = new NotifyAuditRecorder(mapper);

        recorder.complete(
                7002L,
                new NotifyDispatchResult(NotifyDispatchStatus.DELIVERY_FAILED, NotifyType.EMAIL),
                NotifyAuditFailure.CHANNEL_EXCEPTION,
                42L);

        assertEquals(7002L, mapper.completed.getId());
        assertEquals(NotifyType.EMAIL, mapper.completed.getNotifyType());
        assertEquals(NotifySendResult.DELIVERY_FAILED, mapper.completed.getResult());
        assertEquals("CHANNEL_EXCEPTION", mapper.completed.getFailureCode());
        assertEquals("notification channel delivery failed", mapper.completed.getFailureSummary());
        assertEquals(42L, mapper.completed.getDurationMs());
        assertNotNull(mapper.completed.getUpdateTime());
    }

    @Test
    void deliveredCompletionShouldNotPersistFailureText() {
        RecordingMapper mapper = new RecordingMapper();
        NotifyAuditRecorder recorder = new NotifyAuditRecorder(mapper);

        recorder.complete(
                7003L,
                new NotifyDispatchResult(NotifyDispatchStatus.DELIVERED, NotifyType.DINGTALK),
                null,
                -1L);

        assertEquals(NotifySendResult.DELIVERED, mapper.completed.getResult());
        assertNull(mapper.completed.getFailureCode());
        assertNull(mapper.completed.getFailureSummary());
        assertEquals(0L, mapper.completed.getDurationMs());
    }

    private static final class RecordingMapper implements NotifySendLogMapper {
        private NotifySendLog started;
        private NotifySendLog completed;

        @Override
        public int insertStarted(NotifySendLog log) {
            started = log;
            log.setId(7001L);
            return 1;
        }

        @Override
        public int completeIfStarted(NotifySendLog log) {
            completed = log;
            return 1;
        }

        @Override
        public List<NotifySendLogDTO> selectDtoList(NotifySendLogPageQuery query) {
            return List.of();
        }
    }
}
