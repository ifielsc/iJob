package com.ifelio.ijob.core.notify;

import com.github.pagehelper.PageHelper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.PageResult;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.notify.NotificationDeliveryDTO;
import com.ifelio.ijob.contract.notify.NotifySendLogPageQuery;
import com.ifelio.ijob.core.query.PageResults;
import com.ifelio.ijob.infra.mapper.notify.NotificationDeliveryMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {
    private final NotificationDeliveryMapper mapper;

    public PageResult<NotificationDeliveryDTO> queryPage(NotifySendLogPageQuery query) {
        if (query.getDeliveryStatus() != null
                && !java.util.Set.of(
                                "",
                                "READY",
                                "CLAIMED",
                                "RETRY_WAIT",
                                "RATE_WAIT",
                                "DELIVERED",
                                "DEAD")
                        .contains(query.getDeliveryStatus()))
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Notification delivery status is invalid");
        if (query.getFromTime() == null && query.getToTime() == null) {
            query.setToTime(LocalDateTime.now(ZoneOffset.UTC));
            query.setFromTime(query.getToTime().minusDays(1));
        }
        if (query.getFromTime() == null
                || query.getToTime() == null
                || !query.getToTime().isAfter(query.getFromTime())
                || Duration.between(query.getFromTime(), query.getToTime())
                                .compareTo(Duration.ofDays(31))
                        > 0)
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "query time range cannot exceed 31 days");
        PageHelper.startPage(query.getPageNo(), query.getPageSize());
        try {
            return PageResults.from(mapper.selectDtoList(query));
        } finally {
            PageHelper.clearPage();
        }
    }

    public NotificationDeliveryDTO getDetail(Long id) {
        if (id == null || id <= 0)
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Notify config ID is invalid");
        var dto = mapper.selectDtoById(id);
        if (dto == null)
            throw BusinessException.localized(
                    ResultCode.NOT_FOUND, "Notification delivery was not found");
        return dto;
    }
}
