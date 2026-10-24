// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Entry;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.ExportRequest;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.File;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PackageExporter {
    private final PackageObjects objects;
    private final PackageStore store;
    private final PackageArchive archive;

    public File export(ExportRequest request) {
        if (request == null
                || request.roots() == null
                || request.roots().isEmpty()
                || request.roots().size() > 100) throw PackageJson.invalid();
        Walker walker = new Walker(request);
        List<String> roots = new ArrayList<>();
        for (var root : request.roots()) {
            if (root == null || !Set.of(Kind.TASK, Kind.JOB).contains(root.kind()))
                throw PackageJson.invalid();
            roots.add(walker.visit(root.kind(), root.id()));
        }
        for (var snapshot : walker.snapshots.entrySet()) {
            int colon = snapshot.getKey().indexOf(':');
            Kind kind = Kind.valueOf(snapshot.getKey().substring(0, colon));
            String id = snapshot.getKey().substring(colon + 1);
            if (!snapshot.getValue().equals(PackageJson.hash(objects.detail(kind, id))))
                throw PackageJson.conflict();
        }
        return archive.encode(
                new Bundle(
                        1,
                        "1.0.0",
                        objects.timezone(),
                        roots,
                        List.copyOf(walker.entries.values())));
    }

    private final class Walker {
        final ExportRequest options;
        final Map<String, Entry> entries = new LinkedHashMap<>();
        final Map<String, String> snapshots = new LinkedHashMap<>();
        final Map<String, JsonNode> definitions = new LinkedHashMap<>();
        final Set<String> visited = new LinkedHashSet<>();

        Walker(ExportRequest options) {
            this.options = options;
        }

        String visit(Kind kind, String id) {
            if (kind != Kind.PARAMETER_BINDING) PackageJson.id(id);
            String key = store.identity(kind.name(), id);
            if (!visited.add(key)) return key;
            if (visited.size() > 500) throw PackageJson.invalid();
            ObjectNode detail = objects.detail(kind, id);
            snapshots.put(kind + ":" + id, PackageJson.hash(detail));
            definitions.put(
                    kind + ":" + id,
                    detail.path(
                            kind == Kind.TASK ? "currentTaskDefinitionId" : "currentDefinitionId"));
            ObjectNode body = objects.body(kind, detail);
            Map<String, String> refs = new LinkedHashMap<>();
            for (var path : PackageReferences.paths(kind, body).entrySet()) {
                JsonNode value = body.at(path.getKey());
                if (!value.isMissingNode() && !value.isNull()) {
                    refs.put(path.getKey(), visit(path.getValue(), value.asText()));
                    PackageJson.put(body, path.getKey(), PackageJson.JSON.nullNode());
                }
            }
            // Compare against the captured dependency, not an earlier independent read.
            if (kind == Kind.JOB)
                for (JsonNode node : detail.path("nodes")) {
                    if ("EXECUTION".equals(node.path("kind").asText())
                            && !node.path("refDefinitionId")
                                    .equals(
                                            definitions.get(
                                                    node.path("nodeType").asText()
                                                            + ":"
                                                            + node.path("refId").asText())))
                        throw PackageJson.conflict();
                }
            boolean referenceOnly =
                    kind == Kind.NOTIFY
                            || (kind == Kind.RESOURCE || kind == Kind.CONCURRENCY_RESOURCE)
                                    && !options.includeResources();
            List<String> secrets = new ArrayList<>(PackageSecrets.strip(body));
            if (kind == Kind.RESOURCE && !referenceOnly) {
                String field = PackageObjects.resourceField(detail.path("type").asText());
                for (String name :
                        List.of(
                                "password",
                                "privateKey",
                                "passphrase",
                                "accessKey",
                                "secretKey",
                                "sessionToken",
                                "token",
                                "oauth2ClientSecret")) {
                    if (detail.path("config").path(name + "Configured").asBoolean()) {
                        String path =
                                "/"
                                        + field
                                        + "/"
                                        + (name.equals("oauth2ClientSecret")
                                                ? "oauth2/clientSecret"
                                                : name);
                        if (body.at(path).isNull()) secrets.add(path);
                    }
                }
                if (body.path(field).path("hostKeyVerification").asText().equals("MANAGED"))
                    secrets.add("/" + field + "/hostKey");
            }
            collectHeaderSlots(body, "", secrets);
            if (referenceOnly) {
                body = PackageJson.fields(body, "code", "name", "type");
                secrets.clear();
            }
            entries.put(
                    key,
                    new Entry(
                            key,
                            kind,
                            detail.path("name").asText(kind.name()),
                            body,
                            refs,
                            secrets.stream().distinct().toList(),
                            referenceOnly));
            if (kind == Kind.TASK || kind == Kind.JOB) {
                ObjectNode binding = objects.detail(Kind.PARAMETER_BINDING, kind + ":" + id);
                if (binding.hasNonNull("templateId"))
                    visit(Kind.PARAMETER_BINDING, kind + ":" + id);
                if (options.includeSchedules())
                    for (JsonNode s : detail.path("schedules"))
                        visit(Kind.SCHEDULE, s.path("id").asText());
                if (options.includeNotifications())
                    for (JsonNode n : detail.path("notifyBindings"))
                        visit(Kind.NOTIFY_BINDING, n.path("id").asText());
            }
            return key;
        }
    }

    private static void collectHeaderSlots(JsonNode node, String pointer, List<String> secrets) {
        if (node.isObject())
            node.fields()
                    .forEachRemaining(
                            e -> {
                                String p = pointer + "/" + PackageJson.pointer(e.getKey());
                                if (e.getKey().equals("headers") && e.getValue().isObject())
                                    e.getValue()
                                            .fieldNames()
                                            .forEachRemaining(
                                                    name ->
                                                            secrets.add(
                                                                    p
                                                                            + "/"
                                                                            + PackageJson.pointer(
                                                                                    name)));
                                else collectHeaderSlots(e.getValue(), p, secrets);
                            });
        else if (node.isArray())
            for (int i = 0; i < node.size(); i++)
                collectHeaderSlots(node.get(i), pointer + "/" + i, secrets);
    }
}
