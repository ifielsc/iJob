// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Action;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Choice;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Entry;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Plan;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.PlanRequest;
import com.ifielsc.ijob.infra.mapper.coordination.ClusterStateMapper;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

class PackageImporterTest {
    private final String resourceKey = UUID.randomUUID().toString();
    private final MemoryObjects objects = new MemoryObjects();
    private final MemoryStore store = new MemoryStore();
    private final PackageArchive archive = new PackageArchive();
    private final PackageImporter importer = new PackageImporter(objects, archive, store);

    @Test
    void resumedPlanChecksItsOwnDependencyAfterAnotherPlanRemapsTheKey() {
        Plan first = partiallyApplied();
        objects.resources.get("1").put("configurationFingerprint", "edited-configuration");
        Plan second = plan("2");
        assertEquals("COMPLETED", importer.apply(second.id(), "reviewer").status());

        Plan resumed = importer.apply(first.id(), "reviewer");
        assertEquals("PARTIAL", resumed.status());
        assertTrue(resumed.steps().getLast().issues().contains("DEPENDENCY_CHANGED"));
        assertEquals(1, objects.tasks.size());
    }

    @Test
    void unchangedOriginalDependencyCanResumeDespiteUnrelatedRemapping() {
        Plan first = partiallyApplied();
        assertEquals("COMPLETED", importer.apply(plan("2").id(), "reviewer").status());
        objects.resources.get("2").put("configurationFingerprint", "unrelated-edit");

        Plan resumed = importer.apply(first.id(), "reviewer");
        assertEquals("COMPLETED", resumed.status());
        assertEquals(1, objects.lastCreated.at("/pluginConfig/resourceId").asInt());
        assertEquals("VERIFIED", importer.verify(first.id(), "reviewer").status());
        assertEquals(2, objects.tasks.size());
        assertEquals("COMPLETED", importer.apply(first.id(), "reviewer").status());
        assertEquals(2, objects.tasks.size());
    }

    @Test
    void missingDependencyReceiptFailsClosedOnResume() {
        Plan first = partiallyApplied();
        store.data.remove("receipt:" + first.id() + ":" + resourceKey);
        Plan resumed = importer.apply(first.id(), "reviewer");
        assertEquals("PARTIAL", resumed.status());
        assertTrue(resumed.steps().getLast().issues().contains("DEPENDENCY_CHANGED"));
        assertTrue(objects.tasks.isEmpty());
    }

    @Test
    void reusePreviewAndCompletedReportShowRetainedConfiguration() {
        Plan plan = plan("1");
        var step = plan.steps().getFirst();
        assertEquals(Action.REUSE, step.action());
        assertEquals("Destination one", step.after().path("name").asText());
        assertEquals(step.before(), step.after());
        Plan applied = importer.apply(plan.id(), "reviewer");
        assertEquals(step.before(), applied.steps().getFirst().after());
        assertEquals("Destination one", objects.resources.get("1").path("name").asText());
    }

    @Test
    void reuseKeepsEnabledStateInBothProjections() {
        String key = UUID.randomUUID().toString();
        ObjectNode live =
                node(
                        "{\"name\":\"Live"
                            + " task\",\"pluginType\":\"SQL\",\"enabled\":true,\"pluginConfig\":{\"sql\":\"select"
                            + " 2\"}}");
        objects.tasks.put("7", live);
        Entry entry = task(key, Map.of());
        Bundle source = new Bundle(1, "1.0.0", "UTC", List.of(key), List.of(entry));
        Plan plan =
                importer.plan(
                        new PlanRequest(
                                archive.encode(source).content(),
                                Map.of(key, new Choice(Action.REUSE, "7", null, Map.of())),
                                true),
                        "reviewer");
        var step = plan.steps().getFirst();
        assertTrue(step.before().path("enabled").asBoolean());
        assertEquals(step.before(), step.after());
        assertEquals("select 2", step.after().at("/pluginConfig/sql").asText());
        assertEquals("COMPLETED", importer.apply(plan.id(), "reviewer").status());
        assertTrue(objects.tasks.get("7").path("enabled").asBoolean());
    }

    @Test
    void missingReuseTargetHasNoInventedAfterConfiguration() {
        Plan plan =
                importer.plan(
                        new PlanRequest(
                                archive.encode(bundle()).content(),
                                Map.of(resourceKey, new Choice(Action.REUSE, null, null, Map.of())),
                                true),
                        "reviewer");
        assertEquals("BLOCKED", plan.status());
        assertNull(plan.steps().getFirst().after());
    }

    @Test
    void historyIncludesEveryStoragePageThenFiltersOwnerAndSortsByTime() {
        Map<String, String> rows = new HashMap<>();
        String prefix = "ijob:package:v1:";
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 205; i++) {
            String id = String.format("%08x-0000-4000-8000-000000000000", i);
            String actor = i == 0 || i == 100 || i == 204 ? "reviewer" : "someone-else";
            Plan record =
                    new Plan(
                            id,
                            "READY",
                            Instant.EPOCH.plusSeconds(205 - i).toString(),
                            List.of(),
                            "UTC",
                            "UTC");
            rows.put(prefix + "record:" + id, PackageJson.object(record).toString());
            rows.put(prefix + "owner:" + id, actor);
            if (actor.equals("reviewer")) expected.add(id);
        }
        List<Integer> offsets = new ArrayList<>();
        ClusterStateMapper mapper =
                (ClusterStateMapper)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ClusterStateMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("selectValue"))
                                        return rows.get(args[0]);
                                    if (method.getName().equals("searchKeys")) {
                                        offsets.add((Integer) args[2]);
                                        Comparator<String> order =
                                                (Boolean) args[4]
                                                        ? Comparator.naturalOrder()
                                                        : Comparator.reverseOrder();
                                        return rows.keySet().stream()
                                                .filter(k -> k.startsWith((String) args[0]))
                                                .sorted(order)
                                                .skip((Integer) args[2])
                                                .limit((Integer) args[3])
                                                .toList();
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        var persistent = new PackageStore(mapper, null, new AppProperties());
        var history = new PackageImporter(objects, archive, persistent).records("reviewer");
        assertEquals(expected, history.stream().map(r -> r.id()).toList());
        assertEquals(List.of(0, 100, 200), offsets);
        assertTrue(history.stream().allMatch(r -> r.actor().equals("reviewer")));
    }

    private Plan partiallyApplied() {
        Plan plan = plan("1");
        objects.failTask = true;
        Plan partial = importer.apply(plan.id(), "reviewer");
        assertEquals("PARTIAL", partial.status());
        assertEquals("DONE", partial.steps().getFirst().status());
        assertTrue(objects.tasks.isEmpty());
        objects.failTask = false;
        return partial;
    }

    private Plan plan(String destination) {
        Plan plan =
                importer.plan(
                        new PlanRequest(
                                archive.encode(bundle()).content(),
                                Map.of(
                                        resourceKey,
                                        new Choice(Action.REUSE, destination, null, Map.of())),
                                true),
                        "reviewer");
        assertEquals("READY", plan.status());
        return plan;
    }

    private Bundle bundle() {
        String taskKey = UUID.randomUUID().toString();
        Entry resource =
                new Entry(
                        resourceKey,
                        Kind.RESOURCE,
                        "Source resource",
                        node("{\"name\":\"Source resource\",\"type\":\"DB\"}"),
                        Map.of(),
                        List.of(),
                        true);
        return new Bundle(
                1,
                "1.0.0",
                "UTC",
                List.of(taskKey),
                List.of(resource, task(taskKey, Map.of("/pluginConfig/resourceId", resourceKey))));
    }

    private Entry task(String key, Map<String, String> references) {
        return new Entry(
                key,
                Kind.TASK,
                "Source task",
                node(
                        "{\"name\":\"Source"
                            + " task\",\"pluginType\":\"SQL\",\"enabled\":false,\"pluginConfig\":{\"resourceId\":null,\"sql\":\"select"
                            + " 1\"}}"),
                references,
                List.of(),
                false);
    }

    private static ObjectNode node(String json) {
        return PackageJson.read(json, ObjectNode.class);
    }

    private static final class MemoryStore extends PackageStore {
        final Map<String, String> data = new HashMap<>();

        MemoryStore() {
            super(null, null, new AppProperties());
        }

        @Override
        public String get(String key) {
            return data.get(key);
        }

        @Override
        public void put(String key, String value) {
            data.put(key, value);
        }

        @Override
        public void secret(String key, String value) {
            data.put(key, value);
        }

        @Override
        public String secret(String key) {
            return data.get(key);
        }

        @Override
        public <T> T locked(Supplier<T> operation) {
            return operation.get();
        }
    }

    private static final class MemoryObjects extends PackageObjects {
        final Map<String, ObjectNode> resources = new HashMap<>();
        final Map<String, ObjectNode> tasks = new HashMap<>();
        boolean failTask;
        ObjectNode lastCreated;

        MemoryObjects() {
            super(
                    null, null, null, null, null, null, null, null, null, null, null, List.of(),
                    null, null, null, null, null, null, null);
            resources.put(
                    "1",
                    node(
                            "{\"name\":\"Destination"
                                + " one\",\"type\":\"DB\",\"enabled\":true,\"configurationFingerprint\":\"original-one\"}"));
            resources.put(
                    "2",
                    node(
                            "{\"name\":\"Destination"
                                + " two\",\"type\":\"DB\",\"enabled\":true,\"configurationFingerprint\":\"original-two\"}"));
        }

        @Override
        void lockImport() {}

        @Override
        void lock(Kind kind, String id) {}

        @Override
        String timezone() {
            return "UTC";
        }

        @Override
        List<?> candidates(Kind kind, int page) {
            return List.of();
        }

        @Override
        ObjectNode detail(Kind kind, String id) {
            return (kind == Kind.RESOURCE ? resources : tasks).get(id).deepCopy();
        }

        @Override
        ObjectNode body(Kind kind, ObjectNode detail) {
            ObjectNode result = detail.deepCopy();
            result.remove("configurationFingerprint");
            if (kind == Kind.TASK) result.put("enabled", false);
            return result;
        }

        @Override
        void validate(Kind kind, ObjectNode body) {}

        @Override
        String create(Kind kind, ObjectNode body) {
            assertEquals(Kind.TASK, kind);
            if (failTask) throw PackageJson.conflict("TEMPORARY_FIXTURE_FAILURE");
            lastCreated = body.deepCopy();
            String id = Integer.toString(tasks.size() + 10);
            tasks.put(id, body.deepCopy());
            assertFalse(body.path("enabled").asBoolean());
            return id;
        }
    }
}
