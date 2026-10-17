package com.ifelio.ijob.core.execute.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ifelio.ijob.common.exception.BusinessException;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

class VariableResolverTest {
    private final VariableResolver resolver = new VariableResolver();

    @Test
    void scriptResolutionShouldPreserveExplicitlyEscapedNativeExpressions() {
        String script =
                "echo ${HOME}\n"
                        + "echo $${HOME}\n"
                        + "echo $${HOME:-fallback}\n"
                        + "echo ${required!}";

        String resolved =
                resolver.resolveScript(script, Map.of("HOME", "/ijob", "required", "accepted"));

        assertEquals(
                "echo /ijob\n" + "echo ${HOME}\n" + "echo ${HOME:-fallback}\n" + "echo accepted",
                resolved);
    }

    @Test
    void normalResolutionContractShouldNotTreatDoubleDollarAsEscape() {
        assertEquals("$value", resolver.resolve("$${name}", Map.of("name", "value")));
    }

    @Test
    void scriptResolutionShouldKeepNonStrictAndRejectMissingStrictValues() {
        assertEquals("echo ${missing}", resolver.resolveScript("echo ${missing}", Map.of()));
        assertThrows(
                BusinessException.class,
                () -> resolver.resolveScript("echo ${missing!}", Map.of()));
    }

    @Test
    void objectMapMustPreserveExplicitNullAndInsertionOrder() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("optional", null);
        source.put("text", "${value}");
        Map<String, Object> result = resolver.resolveObjectMap(source, Map.of("value", "<safe>"));
        assertEquals(source.keySet().stream().toList(), result.keySet().stream().toList());
        org.junit.jupiter.api.Assertions.assertTrue(result.containsKey("optional"));
        org.junit.jupiter.api.Assertions.assertNull(result.get("optional"));
        assertEquals("<safe>", result.get("text"));
    }
}
