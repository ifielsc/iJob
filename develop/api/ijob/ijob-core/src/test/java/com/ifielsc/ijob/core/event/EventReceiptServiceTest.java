// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.EventSourceType;
import com.ifielsc.ijob.infra.entity.runtime.EventRecord;
import com.ifielsc.ijob.infra.mapper.runtime.EventRecordMapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

class EventReceiptServiceTest {
    @Test
    void payloadNullsRemainDistinctFromMissingFieldsWithHttpMapperDefaults() throws Exception {
        var mapper =
                new StubEventRecordMapper(stored("event-a", "{\"nested\":{\"optional\":null}}"));
        var httpMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var service = new EventReceiptService(mapper, null, null, null, httpMapper);
        var payload = Map.of("nested", java.util.Collections.singletonMap("optional", null));
        assertTrue(service.receive(command("event-a", payload)).duplicate());
        assertThrows(
                BusinessException.class,
                () -> service.receive(command("event-a", Map.of("nested", Map.of()))));
        assertEquals("{\"nested\":{}}", httpMapper.writeValueAsString(payload));
    }

    @Test
    void explicitSubMillisecondTimeShouldMatchPersistedTimestamp() throws Exception {
        EventRecord event = stored("event-a", "{\"key\":\"value\"}");
        event.setOccurredAt(LocalDateTime.parse("2026-09-30T16:45:01.447"));
        EventReceiptService service = service(new StubEventRecordMapper(event));
        EventPublishCommand original = command("event-a", Map.of("key", "value"));
        EventPublishCommand replay = withTime(original, "2026-09-30T16:45:01.4473903");

        assertTrue(service.receive(replay).duplicate());
        assertThrows(
                BusinessException.class,
                () -> service.receive(withTime(original, "2026-09-30T16:45:01.4483903")));
    }

    @Test
    void conflictIgnoredInsertMustReadAndCompareTheWinningEvent() throws Exception {
        var mapper = new StubEventRecordMapper(stored("event-a", "{\"key\":\"value\"}"));
        mapper.race = true;
        assertTrue(service(mapper).receive(command("event-a", Map.of("key", "value"))).duplicate());
        assertEquals(2, mapper.reads);
        var mismatch = new StubEventRecordMapper(stored("event-b", "{\"key\":\"value\"}"));
        mismatch.race = true;
        assertEquals(
                ResultCode.CONFLICT,
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service(mismatch)
                                                .receive(
                                                        command("event-a", Map.of("key", "value"))))
                        .getCode());
    }

    private EventPublishCommand withTime(EventPublishCommand command, String time) {
        return new EventPublishCommand(
                command.eventCode(),
                command.sourceType(),
                command.sourceKey(),
                command.businessKey(),
                command.correlationKey(),
                command.idempotencyKey(),
                LocalDateTime.parse(time),
                command.summary(),
                command.payload());
    }

    @Test
    void identicalRequestShouldReturnPersistedDuplicate() throws Exception {
        StubEventRecordMapper mapper =
                new StubEventRecordMapper(stored("event-a", "{\"key\":\"value\"}"));
        EventReceiptService service = service(mapper);

        EventReceiptResult result = service.receive(command("event-a", Map.of("key", "value")));

        assertTrue(result.duplicate());
        assertEquals(7L, result.eventId());
    }

    @Test
    void reusedKeyWithDifferentContentShouldConflict() throws Exception {
        StubEventRecordMapper mapper =
                new StubEventRecordMapper(stored("event-a", "{\"key\":\"value\"}"));
        EventReceiptService service = service(mapper);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.receive(command("event-b", Map.of("key", "value"))));

        assertEquals(ResultCode.CONFLICT, exception.getCode());
    }

    @Test
    void duplicatePathShouldStillValidatePayloadLimit() throws Exception {
        StubEventRecordMapper mapper =
                new StubEventRecordMapper(stored("event-a", "{\"key\":\"value\"}"));
        EventReceiptService service = service(mapper);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.receive(
                                        command(
                                                "event-a",
                                                "x"
                                                        .repeat(
                                                                EventReceiptService
                                                                        .MAX_PAYLOAD_BYTES))));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }

    private EventReceiptService service(EventRecordMapper mapper) {
        return new EventReceiptService(mapper, null, null, null, new ObjectMapper());
    }

    private EventPublishCommand command(String eventCode, Object payload) {
        return new EventPublishCommand(
                eventCode,
                EventSourceType.HTTP,
                "HTTP:user:source",
                "business",
                "correlation",
                "request-1",
                null,
                "summary",
                payload);
    }

    private EventRecord stored(String eventCode, String payload) throws Exception {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        EventRecord event = new EventRecord();
        event.setId(7L);
        event.setEventCode(eventCode);
        event.setSourceType(EventSourceType.HTTP);
        event.setSourceKey("HTTP:user:source");
        event.setBusinessKey("business");
        event.setCorrelationKey("correlation");
        event.setIdempotencyKey("request-1");
        event.setOccurredAt(LocalDateTime.now());
        event.setArrivedAt(LocalDateTime.now());
        event.setSummary("summary");
        event.setPayload(payload);
        event.setPayloadBytes(bytes.length);
        event.setPayloadDigest(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return event;
    }

    private static final class StubEventRecordMapper implements EventRecordMapper {
        private final EventRecord existing;
        private boolean race;
        private int reads;

        private StubEventRecordMapper(EventRecord existing) {
            this.existing = existing;
        }

        @Override
        public EventRecord selectBySourceAndIdempotency(String sourceKey, String idempotencyKey) {
            return race && reads++ == 0 ? null : existing;
        }

        @Override
        public int insert(EventRecord event) {
            if (race) return 0;
            throw new AssertionError("duplicate request must not insert");
        }

        @Override
        public EventRecord selectById(Long id) {
            return existing;
        }

        @Override
        public EventRecord selectFirstMatching(
                String eventCode,
                String businessKey,
                String correlationKey,
                LocalDateTime acceptAfter) {
            return null;
        }

        @Override
        public List<EventRecord> selectMatchingForWait(
                String eventCode,
                String businessKey,
                String correlationKey,
                LocalDateTime acceptAfter,
                int limit) {
            return List.of();
        }
    }
}
