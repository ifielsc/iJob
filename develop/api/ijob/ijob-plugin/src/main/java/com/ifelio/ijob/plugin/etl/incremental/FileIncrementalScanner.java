package com.ifelio.ijob.plugin.etl.incremental;

import com.ifelio.ijob.core.resource.factory.file.client.FileClient;
import com.ifelio.ijob.core.resource.factory.file.client.FileInfo;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.model.EtlContext;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class FileIncrementalScanner {
    public record Candidate(String path, String relativePath, String marker, FileInfo info) {}

    private record Directory(String relative, int depth) {}

    public List<Candidate> scan(FileClient client, EtlContext context, Object watermark)
            throws IOException {
        var incremental = context.getConfig().getIncremental();
        var config = incremental.getFile();
        var selector = new FileMarkerSelector(incremental);
        String type = incremental.getWatermarkType();
        String lower = watermark.toString();
        if (config.getSelectionMode() == EtlTaskConfig.SelectionMode.LOOKBACK) {
            lower = EtlWatermark.subtract(lower, config.getLookback(), type);
            String initial =
                    EtlWatermark.normalize(
                            incremental.getInitialWatermark(), type, null, config.getZoneId());
            if (EtlWatermark.compare(lower, initial, type) < 0) lower = initial;
        } else if (config.getSelectionMode() == EtlTaskConfig.SelectionMode.LOWER_BOUND) {
            lower = EtlWatermark.normalize(config.getLowerBound(), type, null, config.getZoneId());
        }
        String upper =
                config.getUpperBound() == null
                        ? null
                        : EtlWatermark.normalize(
                                config.getUpperBound(), type, null, config.getZoneId());
        if (upper != null && EtlWatermark.compare(upper, lower, type) < 0)
            throw new IllegalArgumentException("ETL file upper bound precedes lower bound");
        var queue = new ArrayDeque<Directory>();
        queue.add(new Directory("", 0));
        List<Candidate> selected = new ArrayList<>();
        var seen = new HashSet<String>();
        int entries = 0;
        long bytes = 0;
        while (!queue.isEmpty()) {
            context.checkControl();
            Directory directory = queue.removeFirst();
            List<FileInfo> rows =
                    client.list(
                            join(context.getConfig().getSource().getPath(), directory.relative()),
                            config.getMaxScanEntries() - entries + 1);
            if (rows == null) throw new IOException("ETL file listing is unavailable");
            for (FileInfo info : rows) {
                context.checkControl();
                if (++entries > config.getMaxScanEntries())
                    throw new IllegalArgumentException("ETL file scan entry limit exceeded");
                String name = info.getName();
                if (name == null
                        || name.isBlank()
                        || name.equals(".")
                        || name.equals("..")
                        || name.contains("/")
                        || name.contains("\\")
                        || name.indexOf('\0') >= 0)
                    throw new IllegalArgumentException("ETL file entry name is invalid");
                String relative = join(directory.relative(), name);
                if (relative.length() > 2048 || !seen.add(relative))
                    throw new IllegalArgumentException(
                            "ETL file listing contains invalid or duplicate paths");
                if (info.isDirectory()) {
                    if (config.isRecursive()) {
                        if (directory.depth() >= config.getMaxDepth())
                            throw new IllegalArgumentException(
                                    "ETL file scan depth limit exceeded");
                        queue.add(new Directory(relative, directory.depth() + 1));
                    }
                    continue;
                }
                if (config.getReadySuffix() != null && relative.endsWith(config.getReadySuffix()))
                    continue;
                String marker = selector.extract(relative);
                if (marker == null
                        || EtlWatermark.compare(marker, lower, type) < 0
                        || upper != null && EtlWatermark.compare(marker, upper, type) > 0) continue;
                if (info.getSize() < 0
                        || info.getSize() > context.getConfig().getExecution().getMaxFileBytes())
                    throw new IllegalArgumentException("ETL source file size limit exceeded");
                bytes = Math.addExact(bytes, info.getSize());
                if (bytes > config.getMaxTotalBytes() || selected.size() >= config.getMaxFiles())
                    throw new IllegalArgumentException("ETL file batch quota exceeded");
                selected.add(
                        new Candidate(
                                join(context.getConfig().getSource().getPath(), relative),
                                relative,
                                marker,
                                info));
            }
        }
        selected.sort(
                Comparator.<Candidate, String>comparing(
                                Candidate::marker, (a, b) -> EtlWatermark.compare(a, b, type))
                        .thenComparing(Candidate::relativePath));
        context.getTaskContext().putVariable("etl.selectedFiles", selected.size());
        return List.copyOf(selected);
    }

    public boolean ready(
            FileClient client, Candidate candidate, EtlTaskConfig.FileIncrementalConfig config)
            throws IOException {
        if (config.getReadySuffix() != null
                && !client.exists(candidate.path() + config.getReadySuffix())) return false;
        if (config.getStableAgeSeconds() == 0) return true;
        var modified = candidate.info().getLastModified();
        return modified != null
                && !modified.atZone(ZoneId.systemDefault())
                        .toInstant()
                        .plusSeconds(config.getStableAgeSeconds())
                        .isAfter(Instant.now());
    }

    public static String join(String base, String child) {
        if (base.isEmpty()) return child;
        if (child.isEmpty()) return base;
        return base.endsWith("/") ? base + child : base + "/" + child;
    }
}
