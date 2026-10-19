// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class CurrentSchemaTestSupport {
    private CurrentSchemaTestSupport() {}

    static void assertMapperInventory(Configuration configuration, List<String> statementIds) {
        var expected = new TreeSet<String>();
        for (var mapper : configuration.getMapperRegistry().getMappers()) {
            if (!mapper.getName().startsWith("com.ifielsc.ijob.infra.mapper.")) continue;
            Arrays.stream(mapper.getMethods())
                    .filter(method -> Modifier.isAbstract(method.getModifiers()))
                    .forEach(method -> expected.add(mapper.getName() + "." + method.getName()));
        }
        assertEquals(
                expected,
                new TreeSet<>(statementIds),
                "Every mapper operation must have exactly one active dialect statement");
    }

    static void addPlanningParameters(Map<String, Object> values) {
        values.put("loginIds", List.of("operator-it"));
        values.put(
                "category", com.ifielsc.ijob.contract.diagnostics.DiagnosticCategory.LEASE_EXPIRED);
        values.put("type", com.ifielsc.ijob.contract.enums.TargetType.TASK);
    }

    static void selectPlanningType(Map<String, Object> values, String statementId) {
        values.putIfAbsent("planningTimestamp", values.get("now"));
        values.put("key", "ijob-it:rate-limit-planning");
        values.put(
                "deadline",
                statementId.contains("NotifyRateLimitMapper.")
                        ? 2L
                        : values.get("planningTimestamp"));
        values.put(
                "now",
                statementId.contains("NotifyRateLimitMapper.")
                        ? 1L
                        : values.get("planningTimestamp"));
        values.put(
                "type",
                statementId.contains(".parameter.")
                        ? "TASK"
                        : com.ifielsc.ijob.contract.enums.TargetType.TASK);
    }
}
