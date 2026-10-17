package com.ifelio.ijob.core.notify;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.NotifySendSource;
import com.ifelio.ijob.contract.enums.NotifyTestSendStatus;
import com.ifelio.ijob.contract.notify.NotifyTestSendDTO;
import com.ifelio.ijob.contract.notify.NotifyTestSendRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotifyTestSendService {
    private static final int MAX_SUBJECT_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 10_000;

    private final NotifyDispatcher notifyDispatcher;

    public NotifyTestSendDTO testSend(Long notifyConfigId, NotifyTestSendRequest request) {
        return testSend(notifyConfigId, request, null);
    }

    public NotifyTestSendDTO testSend(
            Long notifyConfigId, NotifyTestSendRequest request, String operatorId) {
        validateId(notifyConfigId);
        validateRequest(request);

        NotifyMessage message =
                NotifyMessage.builder()
                        .subject(request.getSubject().trim())
                        .content(request.getContent())
                        .build();
        NotifyDispatchResult result =
                notifyDispatcher.dispatch(
                        notifyConfigId,
                        message,
                        NotifyDispatchContext.builder()
                                .sourceType(NotifySendSource.TEST_SEND)
                                .operatorId(operatorId)
                                .build());

        if (result.failure() == NotifyAuditFailure.RATE_WAIT
                || result.failure() == NotifyAuditFailure.RATE_LIMITED)
            throw BusinessException.localized(
                    ResultCode.SERVICE_UNAVAILABLE,
                    "Notification target is cooling down; test messages are not queued or retried");
        return switch (result.status()) {
            case QUEUED ->
                    throw error(
                            ResultCode.SYSTEM_ERROR, "notify test send was queued unexpectedly");
            case DELIVERED -> delivered(notifyConfigId, result);
            case CONFIG_NOT_FOUND ->
                    throw error(ResultCode.NOT_FOUND, "notify config does not exist");
            case CONFIG_DISABLED -> throw error(ResultCode.CONFLICT, "notify config is disabled");
            case CHANNEL_UNAVAILABLE ->
                    throw error(ResultCode.SERVICE_UNAVAILABLE, "notify channel is unavailable");
            case DELIVERY_FAILED ->
                    throw error(ResultCode.SERVICE_UNAVAILABLE, "notify test send failed");
        };
    }

    private NotifyTestSendDTO delivered(Long notifyConfigId, NotifyDispatchResult result) {
        NotifyTestSendDTO dto = new NotifyTestSendDTO();
        dto.setNotifyConfigId(notifyConfigId);
        dto.setType(result.type());
        dto.setStatus(NotifyTestSendStatus.DELIVERED);
        log.info("notify test send delivered, configId={}, type={}", notifyConfigId, result.type());
        return dto;
    }

    private void validateId(Long notifyConfigId) {
        if (notifyConfigId == null || notifyConfigId <= 0) {
            throw error(ResultCode.PARAM_ERROR, "notify config ID must be greater than 0");
        }
    }

    private void validateRequest(NotifyTestSendRequest request) {
        if (request == null) {
            throw error(ResultCode.PARAM_ERROR, "request body is required");
        }
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw error(ResultCode.PARAM_ERROR, "notification subject is required");
        }
        if (request.getSubject().trim().length() > MAX_SUBJECT_LENGTH) {
            throw error(
                    ResultCode.PARAM_ERROR, "notification subject cannot exceed 255 characters");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw error(ResultCode.PARAM_ERROR, "notification content is required");
        }
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw error(
                    ResultCode.PARAM_ERROR, "notification content cannot exceed 10000 characters");
        }
    }

    private BusinessException error(ResultCode code, String message) {
        return new BusinessException(code, message);
    }
}
