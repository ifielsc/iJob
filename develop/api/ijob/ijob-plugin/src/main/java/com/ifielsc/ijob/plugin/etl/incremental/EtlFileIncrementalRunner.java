// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.incremental;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.core.etl.EtlCheckpointSnapshot;
import com.ifielsc.ijob.core.etl.EtlCheckpointStore;
import com.ifielsc.ijob.core.etl.EtlFileStateStore;
import com.ifielsc.ijob.core.resource.ResourceResolver;
import com.ifielsc.ijob.plugin.etl.EtlPipelineFactory;
import com.ifielsc.ijob.plugin.etl.TempPathResource;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.model.EtlResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EtlFileIncrementalRunner {
    private final EtlCheckpointStore checkpoints;
    private final EtlFileStateStore state;
    private final ResourceResolver resources;
    private final EtlPipelineFactory pipelines;
    private final ObjectMapper mapper;

    public EtlResult execute(EtlContext context) throws IOException {
        var config = context.getConfig();
        var incremental = config.getIncremental();
        var file = incremental.getFile();
        String type = incremental.getWatermarkType();
        String root = config.getSource().getPath();
        String token = UUID.randomUUID().toString();
        String initial =
                EtlWatermark.normalize(
                        incremental.getInitialWatermark(), type, null, file.getZoneId());
        EtlCheckpointSnapshot[] snapshot = {
            checkpoints.loadOrCreate(
                    context.getInstanceId(),
                    incremental.getCheckpointKey(),
                    fingerprint(config),
                    type,
                    initial,
                    List.of())
        };
        snapshot[0] = state.acquire(snapshot[0], context.getInstanceId(), token);
        context.setWriteGuard(
                () -> snapshot[0] = state.verify(snapshot[0], context.getInstanceId(), token));
        long completed = 0, skipped = 0;
        context.getTaskContext().putVariable("etl.completedFiles", 0L);
        context.getTaskContext().putVariable("etl.pendingFiles", 0L);
        context.getTaskContext().putVariable("etl.skippedFiles", 0L);
        try (var lease = resources.leaseFileClient(config.getSource().getResourceId())) {
            context.getTaskContext().registerResource(lease);
            var client = lease.getClient();
            var scanner = new FileIncrementalScanner();
            var candidates = scanner.scan(client, context, snapshot[0].watermark());
            context.getTaskContext()
                    .logInfo("ETL file selection completed, selected=" + candidates.size());
            for (var candidate : candidates) {
                context.checkControl();
                if (!scanner.ready(client, candidate, file)) {
                    context.getTaskContext()
                            .putVariable(
                                    "etl.pendingFiles", candidates.size() - completed - skipped);
                    context.getTaskContext()
                            .logInfo(
                                    "ETL file batch stopped at an unready file; checkpoint"
                                            + " retained");
                    break;
                }
                String suffix =
                        candidate.path().toLowerCase(java.util.Locale.ROOT).endsWith(".xls")
                                ? ".xls"
                                : config.getSource().getFormat() == EtlTaskConfig.FileFormat.EXCEL
                                        ? ".xlsx"
                                        : ".data";
                Path local = Files.createTempFile("ijob-etl-incremental-", suffix);
                try (var temporary = new TempPathResource(local)) {
                    context.getTaskContext().registerResource(temporary);
                    MessageDigest digest = sha256();
                    long bytes = 0;
                    try (var input = client.download(candidate.path());
                            var output = Files.newOutputStream(local)) {
                        context.getTaskContext().registerResource(input);
                        context.getTaskContext().registerResource(output);
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            context.checkControl();
                            bytes = Math.addExact(bytes, count);
                            if (bytes > candidate.info().getSize()
                                    || bytes > config.getExecution().getMaxFileBytes())
                                throw new IllegalArgumentException(
                                        "ETL source file changed or exceeded its quota");
                            digest.update(buffer, 0, count);
                            output.write(buffer, 0, count);
                        }
                    }
                    if (bytes != candidate.info().getSize())
                        throw new IllegalArgumentException(
                                "ETL source file changed during staging");
                    int slash = candidate.path().lastIndexOf('/');
                    String parent = slash < 0 ? "" : candidate.path().substring(0, slash);
                    String name = candidate.path().substring(slash + 1);
                    var current =
                            client.list(parent, file.getMaxScanEntries()).stream()
                                    .filter(
                                            info ->
                                                    name.equals(info.getName())
                                                            && !info.isDirectory())
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "ETL source file disappeared during"
                                                                    + " staging"));
                    if (current.getSize() != bytes
                            || !Objects.equals(
                                    current.getLastModified(), candidate.info().getLastModified()))
                        throw new IllegalArgumentException(
                                "ETL source file changed during staging");
                    String hash = HexFormat.of().formatHex(digest.digest());
                    String pathHash = hash(candidate.relativePath());
                    String previous = state.digest(snapshot[0], pathHash);
                    if (hash.equals(previous)) {
                        skipped++;
                        continue;
                    }
                    if (previous != null
                            && file.getChangedFilePolicy() == EtlTaskConfig.ChangedFilePolicy.FAIL)
                        throw new IllegalArgumentException(
                                "ETL completed file content has changed");
                    context.setStagedSource(local);
                    config.getSource().setPath(candidate.path());
                    pipelines.create(config).execute(context);
                    context.checkControl();
                    Object watermark =
                            EtlWatermark.compare(candidate.marker(), snapshot[0].watermark(), type)
                                            > 0
                                    ? candidate.marker()
                                    : snapshot[0].watermark();
                    snapshot[0] =
                            state.complete(
                                    snapshot[0],
                                    context.getInstanceId(),
                                    token,
                                    pathHash,
                                    hash,
                                    candidate.marker(),
                                    watermark);
                    completed++;
                    context.getTaskContext().putVariable("etl.completedFiles", completed);
                    context.getTaskContext()
                            .logInfo("ETL file completion committed, completed=" + completed);
                } finally {
                    context.setStagedSource(null);
                    config.getSource().setPath(root);
                }
            }
            context.getTaskContext().putVariable("etl.completedFiles", completed);
            context.getTaskContext().putVariable("etl.skippedFiles", skipped);
            context.getTaskContext().putVariable("etl.fileWatermark", snapshot[0].watermark());
            return EtlResult.success(context);
        } finally {
            context.setWriteGuard(() -> {});
            state.release(snapshot[0], token);
        }
    }

    private String fingerprint(EtlTaskConfig config) throws IOException {
        var file = config.getIncremental().getFile();
        var tree = mapper.createObjectNode();
        var sourceTree =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.valueToTree(config.getSource());
        sourceTree.remove(
                java.util.List.of("topic", "groupId", "queue", "initialPosition", "prefetch"));
        tree.set("source", sourceTree);
        tree.set("target", mapper.valueToTree(config.getTarget()));
        tree.set("transforms", mapper.valueToTree(config.getTransforms()));
        tree.set("validations", mapper.valueToTree(config.getValidations()));
        tree.put("type", config.getIncremental().getWatermarkType());
        tree.put("expression", file.getExpression());
        tree.put("location", file.getMarkerLocation().name());
        tree.put("matchMode", file.getMatchMode().name());
        tree.put("format", file.getMarkerFormat());
        tree.put("zone", file.getZoneId());
        return hash(mapper.writeValueAsString(ordered(tree)));
    }

    private com.fasterxml.jackson.databind.JsonNode ordered(
            com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            var names = new java.util.TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, ordered(node.get(name)));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            for (var item : node) result.add(ordered(item));
            return result;
        }
        return node;
    }

    private static String hash(String text) {
        return HexFormat.of().formatHex(sha256().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
