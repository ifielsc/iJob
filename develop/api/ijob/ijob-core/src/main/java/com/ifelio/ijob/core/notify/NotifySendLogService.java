package com.ifelio.ijob.core.notify;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.notify.NotifySendLogDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.mapper.notify.NotifySendLogMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotifySendLogService {
    private static final Duration DEFAULT_RANGE = Duration.ofHours(24);
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final NotifySendLogMapper notifySendLogMapper;

    public PageResult<NotifySendLogDTO> queryPage(NotifySendLogPageQuery query) {
        NotifySendLogPageQuery resolved = query == null ? new NotifySendLogPageQuery() : query;
        validateAndResolve(resolved);

        PageHelper.startPage(resolved.getPageNo(), resolved.getPageSize());
        try {
            return PageResults.from(notifySendLogMapper.selectDtoList(resolved));
        } finally {
            PageHelper.clearPage();
        }
    }

    private void validateAndResolve(NotifySendLogPageQuery query) {
        validateOptionalPositiveId(query.getNotifyConfigId(), "notify config ID");
        validateOptionalPositiveId(query.getTargetId(), "target ID");
        validateOptionalPositiveId(query.getInstanceId(), "instance ID");
        if ((query.getTargetType() == null) != (query.getTargetId() == null)) {
            throw paramError("target type and target ID must be provided together");
        }

        if (query.getFromTime() == null && query.getToTime() == null) {
            LocalDateTime now = LocalDateTime.now();
            query.setFromTime(now.minus(DEFAULT_RANGE));
            query.setToTime(now);
            return;
        }
        if (query.getFromTime() == null || query.getToTime() == null) {
            throw paramError("from time and to time must be provided together");
        }
        if (!query.getToTime().isAfter(query.getFromTime())) {
            throw paramError("to time must be after from time");
        }
        if (Duration.between(query.getFromTime(), query.getToTime()).compareTo(MAX_RANGE) > 0) {
            throw paramError("query time range cannot exceed 31 days");
        }
    }

    private void validateOptionalPositiveId(Long id, String field) {
        if (id != null && id <= 0) {
            throw paramError(field + " must be greater than 0");
        }
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }
}
