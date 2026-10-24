// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.pack;

import com.ifielsc.ijob.contract.pack.JobPackageDTO.Bundle;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.Entry;
import com.ifielsc.ijob.contract.pack.JobPackageDTO.File;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class PackageArchive {
    static final int MAX_BYTES = 16 * 1024 * 1024;

    public File encode(Bundle bundle) {
        validate(bundle);
        try {
            byte[] payload = PackageJson.JSON.writeValueAsBytes(bundle);
            if (payload.length > MAX_BYTES) throw PackageJson.invalid();
            var manifest =
                    PackageJson.JSON
                            .createObjectNode()
                            .put("formatVersion", 1)
                            .put("sha256", PackageJson.hash(payload));
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                write(zip, "manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
                write(zip, "objects.json", payload);
            }
            byte[] result = bytes.toByteArray();
            return new File(
                    "jobs.ijob.zip",
                    Base64.getEncoder().encodeToString(result),
                    PackageJson.hash(result));
        } catch (java.io.IOException e) {
            throw PackageJson.invalid();
        }
    }

    public Bundle decode(String content) {
        if (content == null || content.length() > MAX_BYTES * 4 / 3 + 4)
            throw PackageJson.invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(content);
            if (bytes.length > MAX_BYTES) throw PackageJson.invalid();
            Map<String, byte[]> files = new HashMap<>();
            int total = 0;
            try (var zip =
                    new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()
                            || !Set.of("manifest.json", "objects.json").contains(entry.getName())
                            || files.containsKey(entry.getName())) throw PackageJson.invalid();
                    byte[] data = zip.readNBytes(MAX_BYTES - total + 1);
                    total += data.length;
                    if (total > MAX_BYTES) throw PackageJson.invalid();
                    files.put(entry.getName(), data);
                    zip.closeEntry();
                }
            }
            if (files.size() != 2) throw PackageJson.invalid();
            var manifest = PackageJson.JSON.readTree(files.get("manifest.json"));
            if (manifest.path("formatVersion").asInt() != 1
                    || !PackageJson.hash(files.get("objects.json"))
                            .equals(manifest.path("sha256").asText())) throw PackageJson.invalid();
            Bundle bundle = PackageJson.JSON.readValue(files.get("objects.json"), Bundle.class);
            validate(bundle);
            return bundle;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw PackageJson.invalid();
        }
    }

    static void validate(Bundle bundle) {
        if (bundle == null
                || bundle.formatVersion() != 1
                || !"1.0.0".equals(bundle.productVersion())
                || bundle.objects() == null
                || bundle.objects().isEmpty()
                || bundle.objects().size() > 500
                || bundle.roots() == null
                || bundle.roots().isEmpty()) throw PackageJson.invalid();
        try {
            ZoneId.of(bundle.businessTimezone());
        } catch (Exception e) {
            throw PackageJson.invalid();
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : bundle.objects()) {
            if (entry == null) throw PackageJson.invalid();
            key(entry.key());
            if (entry.kind() == null
                    || entry.body() == null
                    || entry.name() == null
                    || entry.name().length() > 256
                    || entry.references() == null
                    || entry.references().size() > 2000
                    || entry.requiredSecrets() == null
                    || entry.requiredSecrets().size() > 500
                    || entries.put(entry.key(), entry) != null) throw PackageJson.invalid();
            if (!PackageSecrets.strip(entry.body().deepCopy()).isEmpty())
                throw PackageJson.invalid();
            for (String pointer : entry.requiredSecrets()) {
                if (pointer == null
                        || !pointer.startsWith("/")
                        || pointer.length() > 512
                        || !entry.body().at(pointer).isNull()) throw PackageJson.invalid();
            }
            for (var ref : entry.references().entrySet()) {
                if (!ref.getKey().startsWith("/")
                        || ref.getKey().length() > 512
                        || !entry.body().at(ref.getKey()).isNull()) throw PackageJson.invalid();
                key(ref.getValue());
            }
            if (entry.referenceOnly()
                    && entry.kind() != com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind.RESOURCE
                    && entry.kind() != com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind.NOTIFY
                    && entry.kind()
                            != com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind
                                    .CONCURRENCY_RESOURCE) throw PackageJson.invalid();
        }
        for (String root : bundle.roots())
            if (!entries.containsKey(root)
                    || !Set.of(
                                    com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind.TASK,
                                    com.ifielsc.ijob.contract.pack.JobPackageDTO.Kind.JOB)
                            .contains(entries.get(root).kind())) throw PackageJson.invalid();
        ordered(bundle);
    }

    static List<Entry> ordered(Bundle bundle) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        bundle.objects().forEach(e -> entries.put(e.key(), e));
        List<Entry> result = new ArrayList<>();
        Set<String> done = new HashSet<>(), visiting = new HashSet<>();
        for (Entry e : entries.values()) visit(e, entries, done, visiting, result);
        return result;
    }

    private static void visit(
            Entry e,
            Map<String, Entry> all,
            Set<String> done,
            Set<String> visiting,
            List<Entry> out) {
        if (done.contains(e.key())) return;
        if (!visiting.add(e.key())) throw PackageJson.invalid();
        for (String dependency : e.references().values()) {
            Entry d = all.get(dependency);
            if (d == null) throw PackageJson.invalid();
            visit(d, all, done, visiting, out);
        }
        visiting.remove(e.key());
        done.add(e.key());
        out.add(e);
    }

    static void key(String key) {
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key))
                throw PackageJson.invalid();
        } catch (IllegalArgumentException e) {
            throw PackageJson.invalid();
        }
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes)
            throws java.io.IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0);
        zip.putNextEntry(e);
        zip.write(bytes);
        zip.closeEntry();
    }
}
