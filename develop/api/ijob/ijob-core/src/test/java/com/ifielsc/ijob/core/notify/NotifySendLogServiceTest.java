package com.ifielsc.ijob.core.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.PageResult;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.NotifySendResult;
import com.ifielsc.ijob.contract.enums.NotifySendSource;
import com.ifielsc.ijob.contract.enums.TargetType;
import com.ifielsc.ijob.contract.notify.NotifySendLogDTO;
import com.ifielsc.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifielsc.ijob.infra.entity.notify.NotifySendLog;
import com.ifielsc.ijob.infra.mapper.notify.NotifySendLogMapper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

class NotifySendLogServiceTest {
    @Test
    void nullQueryShouldResolveBoundedDefaultWindow() {
        RecordingMapper mapper = new RecordingMapper();
        mapper.items = List.of(new NotifySendLogDTO());
        NotifySendLogService service = new NotifySendLogService(mapper);

        PageResult<NotifySendLogDTO> result = service.queryPage(null);

        assertSame(mapper.items, result.getList());
        assertEquals(
                Duration.ofHours(24),
                Duration.between(mapper.query.getFromTime(), mapper.query.getToTime()));
    }

    @Test
    void explicitFiltersShouldBePreservedAndPageSizeCapped() {
        RecordingMapper mapper = new RecordingMapper();
        NotifySendLogService service = new NotifySendLogService(mapper);
        NotifySendLogPageQuery query = new NotifySendLogPageQuery();
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = from.plusDays(1);
        query.setSourceType(NotifySendSource.INSTANCE);
        query.setResult(NotifySendResult.DELIVERED);
        query.setNotifyConfigId(3L);
        query.setTargetType(TargetType.JOB);
        query.setTargetId(4L);
        query.setInstanceId(5L);
        query.setFromTime(from);
        query.setToTime(to);
        query.setPageSize(500);

        service.queryPage(query);

        assertSame(query, mapper.query);
        assertEquals(from, mapper.query.getFromTime());
        assertEquals(to, mapper.query.getToTime());
        assertEquals(200, mapper.query.getPageSize());
    }

    @Test
    void invalidIdsAndIncompleteTargetShouldFailBeforeMapper() {
        RecordingMapper mapper = new RecordingMapper();
        NotifySendLogService service = new NotifySendLogService(mapper);

        NotifySendLogPageQuery invalidConfig = validQuery();
        invalidConfig.setNotifyConfigId(0L);
        assertParamError(() -> service.queryPage(invalidConfig));

        NotifySendLogPageQuery invalidInstance = validQuery();
        invalidInstance.setInstanceId(-1L);
        assertParamError(() -> service.queryPage(invalidInstance));

        NotifySendLogPageQuery incompleteTarget = validQuery();
        incompleteTarget.setTargetType(TargetType.TASK);
        assertParamError(() -> service.queryPage(incompleteTarget));
        assertNull(mapper.query);
    }

    @Test
    void invalidTimeWindowShouldFailBeforeMapper() {
        RecordingMapper mapper = new RecordingMapper();
        NotifySendLogService service = new NotifySendLogService(mapper);
        NotifySendLogPageQuery missingEnd = new NotifySendLogPageQuery();
        missingEnd.setFromTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertParamError(() -> service.queryPage(missingEnd));

        NotifySendLogPageQuery reversed = validQuery();
        reversed.setToTime(reversed.getFromTime());
        assertParamError(() -> service.queryPage(reversed));

        NotifySendLogPageQuery tooWide = validQuery();
        tooWide.setToTime(tooWide.getFromTime().plusDays(31).plusNanos(1));
        assertParamError(() -> service.queryPage(tooWide));
        assertNull(mapper.query);
    }

    private NotifySendLogPageQuery validQuery() {
        NotifySendLogPageQuery query = new NotifySendLogPageQuery();
        query.setFromTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setToTime(LocalDateTime.of(2026, 8, 2, 0, 0));
        return query;
    }

    private void assertParamError(Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(ResultCode.PARAM_ERROR, error.getCode());
    }

    private static final class RecordingMapper implements NotifySendLogMapper {
        private List<NotifySendLogDTO> items = List.of();
        private NotifySendLogPageQuery query;

        @Override
        public int insertStarted(NotifySendLog log) {
            return 1;
        }

        @Override
        public int completeIfStarted(NotifySendLog log) {
            return 1;
        }

        @Override
        public List<NotifySendLogDTO> selectDtoList(NotifySendLogPageQuery selectedQuery) {
            query = selectedQuery;
            return items;
        }
    }
}
