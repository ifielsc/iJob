// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.parameter.ParameterField;
import com.ifielsc.ijob.contract.parameter.ParameterForm;
import com.ifielsc.ijob.core.execute.variable.VariableSnapshotService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class ParameterRulesTest {
    private ParameterField field(String name, String type) {
        var f = new ParameterField();
        f.setName(name);
        f.setType(type);
        return f;
    }

    @Test
    void rejectsInvalidTypesAndConstraintsWithoutEchoingValues() {
        var f = field("batch", "INTEGER");
        f.setRequired(true);
        f.setMinimum(BigDecimal.ONE);
        f.setMaximum(BigDecimal.TEN);
        for (Object value : List.of("private-value", 11, new BigDecimal("1.2"))) {
            var error =
                    assertThrows(
                            BusinessException.class,
                            () -> ParameterRules.values(List.of(f), Map.of("batch", value)));
            assertTrue(!error.getMessage().contains(value.toString()));
        }
        assertThrows(BusinessException.class, () -> ParameterRules.values(List.of(f), Map.of()));
        ParameterRules.values(List.of(f), Map.of("batch", 3));
        f.setSensitive(true);
        f.setHasDefault(true);
        f.setDefaultValue(3);
        assertThrows(BusinessException.class, () -> ParameterRules.definitions(List.of(f)));
    }

    @Test
    void rejectsDuplicateReservedNamesAndInvalidDates() {
        var f = field("date", "DATE");
        assertThrows(BusinessException.class, () -> ParameterRules.definitions(List.of(f, f)));
        assertThrows(
                BusinessException.class,
                () -> ParameterRules.values(List.of(f), Map.of("date", "2026-02-30")));
        assertThrows(
                BusinessException.class,
                () -> ParameterRules.definitions(List.of(field("ijob.id", "STRING"))));
    }

    @Test
    void savedFormAndValuesAreIndependentOfLaterTemplateChanges() {
        var service = new VariableSnapshotService(new ObjectMapper());
        var f = field("batch", "INTEGER");
        f.setHasDefault(true);
        f.setDefaultValue(1000);
        var form = new ParameterForm();
        form.setTemplateId(1L);
        form.setFields(List.of(f));
        String stored = service.serializeForm(form);
        String values = service.serializeUserVariables(Map.of("batch", 1000), form);
        f.setDefaultValue(10000);
        f.setRequired(true);
        assertEquals(
                1000, service.deserializeForm(stored).getFields().getFirst().getDefaultValue());
        assertEquals(1000, service.deserializeUserVariables(values).get("batch"));
        // Defaults are an editor concern: absent optional values are not re-filled by runtime.
        assertEquals(
                null, service.serializeUserVariables(Map.of(), service.deserializeForm(stored)));
    }

    @Test
    void emptyStringZeroAndFalseAreNotMissing() {
        ParameterRules.values(
                List.of(field("text", "STRING"), field("n", "INTEGER"), field("flag", "BOOLEAN")),
                Map.of("text", "", "n", 0, "flag", false));
    }
}
