package com.ifelio.ijob.core.execute.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.enums.TriggerType;
import com.ifelio.ijob.infra.entity.runtime.Instance;
import com.ifelio.ijob.infra.entity.runtime.Trigger;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

class VariableSnapshotServiceTest {
    private final VariableSnapshotService service = new VariableSnapshotService(new ObjectMapper());

    @Test
    void shouldPreserveNestedNullsAndOutputDeletionWithApiNullOmissionEnabled() {
        ObjectMapper apiMapper =
                new ObjectMapper()
                        .setSerializationInclusion(
                                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        VariableSnapshotService snapshots = new VariableSnapshotService(apiMapper);
        Map<String, Object> nullable = new java.util.LinkedHashMap<>();
        nullable.put("value", null);
        String input = snapshots.serializeUserVariables(Map.of("payload", nullable));
        assertTrue(input.contains("\"value\":null"));
        assertEquals(nullable, snapshots.deserializeUserVariables(input).get("payload"));

        String output = snapshots.serializeOutputs(nullable);
        assertEquals("{\"value\":null}", output);
        assertEquals(
                null, snapshots.mergeUpstreamOutputs("{\"value\":1}", java.util.List.of(output)));
        assertEquals(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL,
                apiMapper
                        .getSerializationConfig()
                        .getDefaultPropertyInclusion()
                        .getValueInclusion());
    }

    @Test
    void shouldPreserveDecimalPrecisionAcrossNestedSnapshotsAndUpstreamMerge() {
        var amount = new java.math.BigDecimal("12345678901234.123456");
        String output =
                service.serializeOutputs(
                        Map.of("row", Map.of("amount", amount, "id", 9007199254740993L)));
        String merged =
                service.mergeUpstreamOutputs("{\"delta\":-0.000001}", java.util.List.of(output));
        Map<String, Object> restored = service.deserializeUserVariables(merged);
        assertEquals(new java.math.BigDecimal("-0.000001"), restored.get("delta"));
        assertEquals(Map.of("amount", amount, "id", 9007199254740993L), restored.get("row"));
        Instance instance = new Instance();
        instance.setInputVariables(merged);
        assertEquals(restored.get("row"), service.buildTaskInputs(instance, null).get("row"));
    }

    @Test
    void shouldRestoreUserValuesAndAddDeterministicBuiltIns() {
        Instance instance = new Instance();
        instance.setId(21L);
        instance.setRootInstanceId(20L);
        instance.setInputVariables("{\"batch\":7,\"empty\":null}");
        Trigger trigger = new Trigger();
        trigger.setId(11L);
        trigger.setScheduleId(9L);
        trigger.setTriggerType(TriggerType.SCHEDULE);
        trigger.setTriggerTime(LocalDateTime.of(2026, 8, 25, 10, 30, 15));

        Map<String, Object> inputs = service.buildTaskInputs(instance, trigger);

        assertEquals(7, inputs.get("batch"));
        assertFalse(inputs.containsKey("empty"));
        assertEquals(21L, inputs.get("ijob.instance.id"));
        assertEquals(20L, inputs.get("ijob.instance.rootId"));
        assertEquals(11L, inputs.get("ijob.trigger.id"));
        assertEquals("SCHEDULE", inputs.get("ijob.trigger.type"));
        assertEquals("2026-08-25T10:30:15", inputs.get("ijob.trigger.time"));
        assertEquals("2026-08-25", inputs.get("ijob.trigger.date"));
        assertEquals(9L, inputs.get("ijob.schedule.id"));
    }

    @Test
    void shouldRejectReservedUserVariableNames() {
        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.serializeUserVariables(Map.of("ijob.trigger.id", 99L)));

        assertEquals(ResultCode.PARAM_ERROR, exception.getCode());
    }

    @Test
    void shouldMergeOutputsAndRejectAmbiguousParallelNames() {
        String merged =
                service.mergeUpstreamOutputs(
                        "{\"input\":\"base\",\"removed\":1}",
                        java.util.List.of("{\"value\":7,\"removed\":null}"));

        assertEquals(Map.of("input", "base", "value", 7), service.deserializeUserVariables(merged));

        BusinessException conflict =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.mergeUpstreamOutputs(
                                        null, java.util.List.of("{\"same\":1}", "{\"same\":2}")));
        assertEquals(ResultCode.CONFLICT, conflict.getCode());
    }
}
