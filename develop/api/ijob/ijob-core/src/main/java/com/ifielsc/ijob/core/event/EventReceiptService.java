package com.ifielsc.ijob.core.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.trigger.TriggerService;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class EventReceiptService {
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final EventRecordMapper eventRecordMapper;
    private final EventBindingService eventBindingService;
    private final TriggerService triggerService;
    private final EventWaitResolutionService waitResolutionService;
    private final ObjectMapper objectMapper;

    public EventReceiptService(
            EventRecordMapper eventRecordMapper,
            EventBindingService eventBindingService,
            TriggerService triggerService,
            EventWaitResolutionService waitResolutionService,
            ObjectMapper objectMapper) {
        this.eventRecordMapper = eventRecordMapper;
        this.eventBindingService = eventBindingService;
        this.triggerService = triggerService;
        this.waitResolutionService = waitResolutionService;
        // Explicit JSON nulls are payload data and participate in the idempotency digest.
        this.objectMapper =
                objectMapper
                        .copy()
                        .setDefaultPropertyInclusion(
                                JsonInclude.Value.construct(
                                        JsonInclude.Include.ALWAYS, JsonInclude.Include.ALWAYS));
    }

    @Transactional
    public EventReceiptResult receive(EventPublishCommand command) {
        validate(command);
        EventRecord event = toEvent(command);
        EventRecord existing =
                eventRecordMapper.selectBySourceAndIdempotency(
                        event.getSourceKey(), event.getIdempotencyKey());
        if (existing != null) {
            requireSameEvent(existing, event, command.occurredAt() != null);
            return new EventReceiptResult(existing.getId(), true);
        }

        try {
            int inserted = eventRecordMapper.insert(event);
            if (inserted == 0) {
                EventRecord raced =
                        eventRecordMapper.selectBySourceAndIdempotency(
                                event.getSourceKey(), event.getIdempotencyKey());
                if (raced == null) throw systemError("Event creation failed");
                requireSameEvent(raced, event, command.occurredAt() != null);
                return new EventReceiptResult(raced.getId(), true);
            }
            if (inserted != 1) throw systemError("Event creation failed");
        } catch (DuplicateKeyException duplicate) {
            EventRecord raced =
                    eventRecordMapper.selectBySourceAndIdempotency(
                            event.getSourceKey(), event.getIdempotencyKey());
            if (raced == null) {
                throw duplicate;
            }
            requireSameEvent(raced, event, command.occurredAt() != null);
            return new EventReceiptResult(raced.getId(), true);
        }

        waitResolutionService.match(event);
        eventBindingService
                .listBindingsByEventCode(event.getEventCode())
                .forEach(binding -> triggerService.trigger(binding, event.getId()));
        return new EventReceiptResult(event.getId(), false);
    }

    private void requireSameEvent(
            EventRecord existing, EventRecord requested, boolean compareOccurredAt) {
        if (!Objects.equals(existing.getEventCode(), requested.getEventCode())
                || existing.getSourceType() != requested.getSourceType()
                || !Objects.equals(existing.getBusinessKey(), requested.getBusinessKey())
                || !Objects.equals(existing.getCorrelationKey(), requested.getCorrelationKey())
                || !Objects.equals(existing.getSummary(), requested.getSummary())
                || !Objects.equals(existing.getPayloadDigest(), requested.getPayloadDigest())
                || !Objects.equals(existing.getPayloadBytes(), requested.getPayloadBytes())
                || compareOccurredAt
                        && !Objects.equals(existing.getOccurredAt(), requested.getOccurredAt())) {
            throw BusinessException.localized(
                    ResultCode.CONFLICT, "Event idempotency key was reused with different content");
        }
    }

    private EventRecord toEvent(EventPublishCommand command) {
        String payload = writePayload(command.payload());
        byte[] bytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        EventRecord event = new EventRecord();
        event.setEventCode(command.eventCode().trim());
        event.setSourceType(command.sourceType());
        event.setSourceKey(command.sourceKey().trim());
        event.setBusinessKey(trimToNull(command.businessKey()));
        event.setCorrelationKey(trimToNull(command.correlationKey()));
        event.setIdempotencyKey(command.idempotencyKey().trim());
        event.setOccurredAt(
                // All supported stores persist timestamps at millisecond precision.
                // Normalize before insertion and duplicate comparison, not after a DB round trip.
                (command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt())
                        .truncatedTo(ChronoUnit.MILLIS));
        event.setArrivedAt(LocalDateTime.now());
        event.setSummary(trimToNull(command.summary()));
        event.setPayload(payload);
        event.setPayloadBytes(bytes.length);
        event.setPayloadDigest(bytes.length == 0 ? null : digest(bytes));
        return event;
    }

    private String writePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw BusinessException.localized(
                        ResultCode.PARAM_ERROR, "Event payload exceeds 16384 bytes");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw BusinessException.localized(
                    ResultCode.PARAM_ERROR, "Event payload must be JSON serializable", exception);
        }
    }

    private String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw systemError("SHA-256 is unavailable");
        }
    }

    private void validate(EventPublishCommand command) {
        if (command == null || command.sourceType() == null) {
            throw paramError("Event source is required");
        }
        requireText(command.eventCode(), 128, "Event code");
        if (!EVENT_CODE.matcher(command.eventCode()).matches()) {
            throw paramError("Event code is invalid");
        }
        requireText(command.sourceKey(), 128, "Event source key");
        requireText(command.idempotencyKey(), 256, "Event idempotency key");
        optional(command.businessKey(), 256, "Event business key");
        optional(command.correlationKey(), 256, "Event correlation key");
        optional(command.summary(), 500, "Event summary");
    }

    private void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw paramError(field + " is invalid");
        }
    }

    private void optional(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw paramError(field + " is invalid");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException paramError(String message) {
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }

    private BusinessException systemError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }
}
