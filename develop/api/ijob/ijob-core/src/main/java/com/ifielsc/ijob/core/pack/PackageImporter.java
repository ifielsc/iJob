// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.pack.JobPackageDTO;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Action;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Choice;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Entry;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Plan;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.PlanRequest;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Step;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PackageImporter {
    private final PackageObjects objects;
    private final PackageArchive archive;
    private final PackageStore store;

    record Mapping(Kind kind, String targetId, String desiredHash, String observedHash) {}

    record Saved(
            String actor,
            Bundle bundle,
            Map<String, Choice> choices,
            Map<String, String> expected,
            Map<String, String> previousMappings) {
        @Override
        public String toString() {
            return "Saved[redacted]";
        }
    }

    public Bundle inspect(String content) {
        Bundle bundle = archive.decode(content);
        validateReferences(bundle);
        return bundle;
    }

    public Plan plan(PlanRequest request, String actor) {
        if (request == null || request.choices() == null || request.choices().size() > 500)
            throw PackageJson.invalid();
        Bundle bundle = inspect(request.content());
        Set<String> keys = new HashSet<>();
        bundle.objects().forEach(e -> keys.add(e.key()));
        if (!keys.containsAll(request.choices().keySet())) throw PackageJson.invalid();
        List<Step> steps = new ArrayList<>();
        Map<String, Choice> choices = new LinkedHashMap<>();
        Map<String, String> expected = new HashMap<>(), previousMappings = new HashMap<>();
        for (Entry entry : PackageArchive.ordered(bundle)) {
            Choice supplied = request.choices().get(entry.key());
            String prior = store.get("mapping:" + entry.key());
            Mapping mapping = prior == null ? null : PackageJson.read(prior, Mapping.class);
            if (mapping != null && mapping.kind() != entry.kind()) throw PackageJson.conflict();
            previousMappings.put(entry.key(), prior == null ? "" : prior);
            Choice choice =
                    supplied == null
                            ? new Choice(
                                    mapping == null ? Action.CREATE : Action.REUSE,
                                    mapping == null ? null : mapping.targetId(),
                                    null,
                                    Map.of())
                            : supplied;
            if (choice.action() == null
                    || choice.secrets() != null && choice.secrets().size() > 500)
                throw PackageJson.invalid();
            choices.put(entry.key(), choice);
            ObjectNode desired = desired(entry, choice, false);
            List<String> issues = new ArrayList<>();
            ObjectNode before = null;
            String status = choice.action().name();
            if (!request.reviewedEnvironment()) issues.add("REVIEW_ENVIRONMENT");
            if (choice.action() == Action.CREATE) {
                if (choice.targetId() != null) throw PackageJson.invalid();
                if (mapping != null) issues.add("ALREADY_IMPORTED");
                if (entry.referenceOnly()) issues.add("MAP_REQUIRED");
                if (sameName(entry.kind(), desired)) issues.add("NAME_CONFLICT");
            } else {
                if (choice.targetId() == null) issues.add("MAP_REQUIRED");
                else
                    try {
                        before = objects.detail(entry.kind(), choice.targetId());
                        expected.put(entry.key(), fingerprint(before));
                        if (!compatible(entry, before)) issues.add("INCOMPATIBLE_TARGET");
                        if (choice.action() == Action.UPDATE) {
                            if (entry.kind() != Kind.TASK && entry.kind() != Kind.JOB)
                                issues.add("UPDATE_UNSUPPORTED");
                            if (before.path("enabled").asBoolean() || hasEnabledSchedule(before))
                                issues.add("DISABLE_BEFORE_UPDATE");
                        }
                        if (supplied == null && mapping != null) {
                            if (!mapping.observedHash().equals(fingerprint(before)))
                                issues.add("TARGET_CHANGED");
                            if (!entry.referenceOnly()
                                    && !mapping.desiredHash().equals(desiredHash(entry, choice)))
                                issues.add("CHANGED_REQUIRES_CHOICE");
                            if (issues.isEmpty()) status = "UNCHANGED";
                        }
                    } catch (BusinessException e) {
                        issues.add("TARGET_MISSING");
                    }
            }
            if (choice.action() != Action.REUSE) {
                for (String path : entry.requiredSecrets())
                    if (desired.at(path).isNull()
                            || desired.at(path).isMissingNode()
                            || desired.at(path).asText().isBlank())
                        issues.add("MISSING_INPUT:" + path);
                ObjectNode validation = desired.deepCopy();
                for (String path : entry.references().keySet())
                    PackageJson.put(
                            validation, path, PackageJson.JSON.getNodeFactory().numberNode(1));
                try {
                    objects.validate(entry.kind(), validation);
                } catch (BusinessException e) {
                    issues.add("INVALID_CONFIGURATION");
                }
            }
            ObjectNode publicBefore = before == null ? null : publicBody(entry, before);
            steps.add(
                    new Step(
                            entry.key(),
                            entry.kind(),
                            desired.path("name").asText(entry.name()),
                            choice.action(),
                            choice.targetId(),
                            issues.isEmpty() ? status : "BLOCKED",
                            List.copyOf(issues),
                            publicBefore,
                            choice.action() == Action.REUSE
                                    ? publicBefore
                                    : PackageSecrets.safe(desired, entry.requiredSecrets())));
        }
        String id = UUID.randomUUID().toString();
        Plan plan =
                new Plan(
                        id,
                        steps.stream().anyMatch(s -> !s.issues().isEmpty()) ? "BLOCKED" : "READY",
                        Instant.now().toString(),
                        steps,
                        bundle.businessTimezone(),
                        objects.timezone());
        Saved saved = new Saved(actor, bundle, choices, expected, previousMappings);
        store.secret("plan:" + id, PackageJson.object(saved).toString());
        store.put("owner:" + id, actor);
        store.put("record:" + id, PackageJson.object(plan).toString());
        return get(id, actor);
    }

    public Plan get(String id, String actor) {
        PackageArchive.key(id);
        owner(id, actor);
        String record = store.get("record:" + id);
        if (record == null) throw new BusinessException(ResultCode.NOT_FOUND);
        Plan p = PackageJson.read(record, Plan.class);
        return new Plan(
                p.id(),
                p.status(),
                p.createdAt(),
                p.steps(),
                p.sourceTimezone(),
                p.targetTimezone(),
                actor,
                store.get("started:" + id),
                store.get("completed:" + id));
    }

    public List<JobPackageDTO.Record> records(String actor) {
        List<JobPackageDTO.Record> result = new ArrayList<>();
        for (String key : store.records()) {
            String id = key.substring("record:".length());
            if (!actor.equals(store.get("owner:" + id))) continue;
            Plan p = get(id, actor);
            result.add(
                    new JobPackageDTO.Record(
                            p.id(),
                            p.status(),
                            p.createdAt(),
                            p.steps().size(),
                            (int) p.steps().stream().filter(s -> "DONE".equals(s.status())).count(),
                            p.actor(),
                            p.startedAt(),
                            p.completedAt()));
        }
        result.sort(
                java.util.Comparator.comparing(JobPackageDTO.Record::createdAt)
                        .reversed()
                        .thenComparing(JobPackageDTO.Record::id));
        return result;
    }

    public JobPackageDTO.Verification verify(String id, String actor) {
        Plan plan = get(id, actor);
        List<String> changed = new ArrayList<>();
        if (!"COMPLETED".equals(plan.status()))
            return new JobPackageDTO.Verification(id, "INCOMPLETE", List.of());
        for (Step step : plan.steps()) {
            try {
                if (!fingerprint(objects.detail(step.kind(), step.targetId()))
                        .equals(store.get("receipt:" + id + ":" + step.key())))
                    changed.add(step.key());
            } catch (BusinessException e) {
                changed.add(step.key());
            }
        }
        return new JobPackageDTO.Verification(
                id, changed.isEmpty() ? "VERIFIED" : "DRIFTED", List.copyOf(changed));
    }

    public Plan apply(String id, String actor) {
        Plan initial = get(id, actor);
        if ("COMPLETED".equals(initial.status())) return initial;
        if ("BLOCKED".equals(initial.status())) throw PackageJson.conflict();
        String encrypted = store.secret("plan:" + id);
        if (encrypted == null) throw new BusinessException(ResultCode.NOT_FOUND);
        Saved saved = PackageJson.read(encrypted, Saved.class);
        if (!actor.equals(saved.actor())) throw new BusinessException(ResultCode.FORBIDDEN);
        store.locked(
                () -> {
                    if (store.get("started:" + id) == null)
                        store.put("started:" + id, Instant.now().toString());
                    return null;
                });
        List<Entry> entries = PackageArchive.ordered(saved.bundle());
        for (Entry entry : entries) {
            try {
                store.locked(() -> applyOne(id, actor, saved, entry));
            } catch (BusinessException | org.springframework.dao.DataAccessException e) {
                return store.locked(
                        () -> {
                            Plan current = get(id, actor);
                            if ("COMPLETED".equals(current.status())) return current;
                            List<Step> steps = new ArrayList<>(current.steps());
                            for (int i = 0; i < steps.size(); i++)
                                if (steps.get(i).key().equals(entry.key())
                                        && !steps.get(i).status().equals("DONE")) {
                                    Step s = steps.get(i);
                                    steps.set(
                                            i,
                                            new Step(
                                                    s.key(),
                                                    s.kind(),
                                                    s.name(),
                                                    s.action(),
                                                    s.targetId(),
                                                    "FAILED",
                                                    List.of(
                                                            e
                                                                            instanceof
                                                                            PackageJson.Conflict
                                                                                            conflict
                                                                    ? conflict.reason
                                                                    : e
                                                                                    instanceof
                                                                                    BusinessException
                                                                                                    business
                                                                            ? business
                                                                                    .getErrorCode()
                                                                            : "PERSISTENCE_CONFLICT"),
                                                    s.before(),
                                                    s.after()));
                                }
                            Plan failed =
                                    new Plan(
                                            id,
                                            "PARTIAL",
                                            current.createdAt(),
                                            steps,
                                            current.sourceTimezone(),
                                            current.targetTimezone());
                            save(failed);
                            return get(id, actor);
                        });
            }
        }
        return get(id, actor);
    }

    private Plan applyOne(String id, String actor, Saved saved, Entry entry) {
        objects.lockImport();
        Plan current = get(id, actor);
        List<Step> steps = new ArrayList<>(current.steps());
        int index = -1;
        for (int i = 0; i < steps.size(); i++)
            if (steps.get(i).key().equals(entry.key())) {
                index = i;
                break;
            }
        if (index < 0) throw PackageJson.invalid();
        Step step = steps.get(index);
        if (step.status().equals("DONE")) return current;
        String mappingValue = store.get("mapping:" + entry.key());
        if (!saved.previousMappings()
                .get(entry.key())
                .equals(mappingValue == null ? "" : mappingValue))
            throw PackageJson.conflict("MAPPING_CHANGED");
        Choice choice = saved.choices().get(entry.key());
        ObjectNode body = desired(entry, choice, false);
        Map<String, Step> resolved = new HashMap<>();
        for (Step done : steps) if (done.status().equals("DONE")) resolved.put(done.key(), done);
        // Verify the target this plan will use, even if another plan has since remapped its key.
        for (String dependency : new java.util.TreeSet<>(entry.references().values())) {
            Step done = resolved.get(dependency);
            if (done == null) throw PackageJson.conflict("DEPENDENCY_PENDING");
            String receipt = store.get("receipt:" + id + ":" + dependency);
            objects.lock(done.kind(), done.targetId());
            if (receipt == null
                    || !receipt.equals(fingerprint(objects.detail(done.kind(), done.targetId()))))
                throw PackageJson.conflict("DEPENDENCY_CHANGED");
        }
        for (var ref : entry.references().entrySet()) {
            Step done = resolved.get(ref.getValue());
            if (done == null) throw PackageJson.conflict("DEPENDENCY_PENDING");
            PackageJson.put(
                    body,
                    ref.getKey(),
                    PackageJson.JSON.getNodeFactory().numberNode(PackageJson.id(done.targetId())));
        }
        String target = choice.targetId();
        ObjectNode before = null;
        if (target != null) {
            objects.lock(entry.kind(), target);
            before = objects.detail(entry.kind(), target);
            if (!fingerprint(before).equals(saved.expected().get(entry.key())))
                throw PackageJson.conflict("TARGET_CHANGED");
            if (!compatible(entry, before)) throw PackageJson.conflict("INCOMPATIBLE_TARGET");
        }
        if (choice.action() == Action.CREATE) {
            if (sameName(entry.kind(), body)) throw PackageJson.conflict();
            target = objects.create(entry.kind(), body);
            store.put("identity:" + entry.kind() + ":" + target, entry.key());
        } else if (choice.action() == Action.UPDATE)
            objects.update(entry.kind(), target, before, body);
        ObjectNode observed = objects.detail(entry.kind(), target);
        Mapping mapping =
                new Mapping(
                        entry.kind(), target, desiredHash(entry, choice), fingerprint(observed));
        store.put("mapping:" + entry.key(), PackageJson.object(mapping).toString());
        store.put("receipt:" + id + ":" + entry.key(), mapping.observedHash());
        steps.set(
                index,
                new Step(
                        step.key(),
                        step.kind(),
                        step.name(),
                        step.action(),
                        target,
                        "DONE",
                        List.of(),
                        step.before(),
                        choice.action() == Action.REUSE
                                ? publicBody(entry, observed)
                                : step.after()));
        Plan result =
                new Plan(
                        id,
                        steps.stream().allMatch(s -> s.status().equals("DONE"))
                                ? "COMPLETED"
                                : "APPLYING",
                        current.createdAt(),
                        steps,
                        current.sourceTimezone(),
                        current.targetTimezone());
        save(result);
        return result;
    }

    private ObjectNode publicBody(Entry entry, ObjectNode detail) {
        ObjectNode body = objects.body(entry.kind(), detail);
        // Export disables portable definitions; comparison must retain the live enabled state.
        if (body.has("enabled") && detail.has("enabled"))
            body.set("enabled", detail.get("enabled").deepCopy());
        return PackageSecrets.safe(body, entry.requiredSecrets());
    }

    private static String fingerprint(ObjectNode detail) {
        ObjectNode value = detail.deepCopy();
        value.remove(List.of("schedules", "notifyBindings"));
        for (JsonNode node : value.path("nodes"))
            if (node instanceof ObjectNode n)
                n.remove(
                        List.of(
                                "currentRefName",
                                "currentRefDefinitionId",
                                "currentRefRevision",
                                "referenceAvailable"));
        return PackageJson.hash(value);
    }

    private void save(Plan plan) {
        if ("COMPLETED".equals(plan.status()) && store.get("completed:" + plan.id()) == null)
            store.put("completed:" + plan.id(), Instant.now().toString());
        store.put("record:" + plan.id(), PackageJson.object(plan).toString());
    }

    private void owner(String id, String actor) {
        if (!actor.equals(store.get("owner:" + id)))
            throw new BusinessException(ResultCode.NOT_FOUND);
    }

    private static String desiredHash(Entry entry, Choice choice) {
        ObjectNode value = PackageJson.JSON.createObjectNode();
        value.set(
                "body", PackageSecrets.safe(desired(entry, choice, true), entry.requiredSecrets()));
        value.set("references", PackageJson.JSON.valueToTree(entry.references()));
        return PackageJson.hash(value);
    }

    private static ObjectNode desired(Entry entry, Choice choice, boolean omitSecrets) {
        ObjectNode body =
                choice.body() == null ? entry.body().deepCopy() : choice.body().deepCopy();
        // Rebinding is controlled solely by the verified reference graph.
        for (String path : entry.references().keySet())
            if (!body.at(path).isNull()) throw PackageJson.invalid();
        Map<String, Kind> paths = PackageReferences.paths(entry.kind(), body);
        if (!paths.keySet().containsAll(entry.references().keySet())) throw PackageJson.invalid();
        for (String path : paths.keySet())
            if (!body.at(path).isMissingNode() && !body.at(path).isNull())
                throw PackageJson.invalid();
        if (!omitSecrets && choice.secrets() != null)
            for (var input : choice.secrets().entrySet()) {
                if (!entry.requiredSecrets().contains(input.getKey())
                        || input.getValue() == null
                        || input.getValue().length() > 65536) throw PackageJson.invalid();
                PackageJson.put(
                        body,
                        input.getKey(),
                        PackageJson.JSON.getNodeFactory().textNode(input.getValue()));
            }
        if (entry.kind() == Kind.TASK || entry.kind() == Kind.JOB || entry.kind() == Kind.SCHEDULE)
            body.put("enabled", false);
        return body;
    }

    private static boolean compatible(Entry entry, ObjectNode live) {
        if (entry.kind() == Kind.RESOURCE || entry.kind() == Kind.NOTIFY)
            return entry.body().path("type").equals(live.path("type"))
                    && live.path("enabled").asBoolean(false);
        if (entry.kind() == Kind.CONCURRENCY_RESOURCE) return live.path("enabled").asBoolean(false);
        if (entry.kind() == Kind.TASK)
            return entry.body().path("pluginType").equals(live.path("pluginType"));
        return true;
    }

    private static boolean hasEnabledSchedule(ObjectNode detail) {
        for (JsonNode s : detail.path("schedules")) if (s.path("enabled").asBoolean()) return true;
        return false;
    }

    private boolean sameName(Kind kind, ObjectNode body) {
        if (!body.hasNonNull("name")) return false;
        for (int page = 1; page <= 100; page++) {
            List<?> rows = objects.candidates(kind, page);
            for (Object row : rows) {
                ObjectNode n = PackageJson.object(row);
                if (body.path("name").asText().equalsIgnoreCase(n.path("name").asText())
                        || (kind == Kind.RESOURCE || kind == Kind.CONCURRENCY_RESOURCE)
                                && body.path("code")
                                        .asText()
                                        .equalsIgnoreCase(n.path("code").asText())) return true;
            }
            if (rows.size() < 100) return false;
        }
        throw PackageJson.conflict();
    }

    private static void validateReferences(Bundle bundle) {
        Map<String, Entry> all = new HashMap<>();
        bundle.objects().forEach(e -> all.put(e.key(), e));
        for (Entry e : bundle.objects()) {
            Map<String, Kind> paths = PackageReferences.paths(e.kind(), e.body());
            for (var ref : e.references().entrySet())
                if (paths.get(ref.getKey()) != all.get(ref.getValue()).kind())
                    throw PackageJson.invalid();
            for (String path : paths.keySet())
                if (!e.body().at(path).isMissingNode() && !e.body().at(path).isNull())
                    throw PackageJson.invalid();
        }
    }

    public List<Map<String, String>> candidates(Kind kind, int page) {
        if (page < 1 || page > 1000) throw PackageJson.invalid();
        return objects.candidates(kind, page).stream()
                .map(
                        o -> {
                            ObjectNode n = PackageJson.object(o);
                            Map<String, String> row = new LinkedHashMap<>();
                            row.put("id", n.path("id").asText());
                            row.put("name", n.path("name").asText());
                            row.put("type", n.path("type").asText(n.path("pluginType").asText()));
                            return row;
                        })
                .toList();
    }
}
