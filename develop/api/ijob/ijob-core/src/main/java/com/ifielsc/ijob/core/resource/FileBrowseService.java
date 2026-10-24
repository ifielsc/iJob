// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.FileBrowseDTO;
import com.ifielsc.ijob.core.resource.factory.ResourceClientFactory;
import com.ifielsc.ijob.core.resource.factory.file.FileResourceConfig;
import com.ifielsc.ijob.core.resource.factory.file.client.FileClient;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Short-lived, encrypted browser sessions never modify the saved resource or remote files. */
@Service
@RequiredArgsConstructor
public class FileBrowseService {
    private final ResourceService resources;
    private final ResourceConfigService configs;
    private final ResourceConfigCipher cipher;
    private final List<ResourceClientFactory> factories;
    private final ClusterStateStore store;
    private final AppProperties properties;
    private final ObjectMapper json;
    private final Semaphore slots = new Semaphore(4);
    private static final int TTL = 120;

    public record Saved(
            Long resourceId, String config, String protocol, String root, String node) {}

    public record Snapshot(String path, List<FileBrowseDTO.Entry> entries) {}

    public List<FileBrowseDTO.Root> roots() {
        List<FileBrowseDTO.Root> result = new ArrayList<>();
        var roots = properties.getResourceBrowser().getLocalRoots();
        for (int i = 0; i < roots.size(); i++) {
            Path root = Path.of(roots.get(i));
            if (root.isAbsolute() && Files.isDirectory(root))
                result.add(new FileBrowseDTO.Root(String.valueOf(i), root.normalize().toString()));
        }
        return result;
    }

    public FileBrowseDTO.Session open(FileBrowseDTO.Request request, String actor) {
        if (request == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        String prefix = prefix(actor);
        if (store.search(prefix, "", 0, 17, true).size() >= 16)
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);
        Resource existing = request.resourceId() == null ? null : resource(request.resourceId());
        Resource temporary = new Resource();
        temporary.setType(ResourceType.FILE);
        String name = existing == null ? "" : existing.getName();
        if (request.draft() != null) {
            if (request.draft().getType() != ResourceType.FILE
                    || request.draft().getFileConfig() == null)
                throw new BusinessException(ResultCode.PARAM_ERROR);
            var draft = request.draft();
            if ("LOCAL".equals(draft.getFileConfig().getProtocol())) {
                var root =
                        roots().stream()
                                .filter(r -> r.id().equals(request.localRootId()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN));
                draft.getFileConfig().setBasePath(root.path());
            }
            temporary.setConfig(configs.protect(draft, existing));
            name = draft.getName() == null ? "" : draft.getName();
        } else {
            if (existing == null) throw new BusinessException(ResultCode.PARAM_ERROR);
            temporary.setConfig(existing.getConfig());
        }
        String plaintext = configs.decryptForRuntime(temporary);
        try {
            FileResourceConfig config = json.readValue(plaintext, FileResourceConfig.class);
            // A browser has a tighter connection timeout than task execution.
            config.setTimeout(Math.min(10000, Math.max(1000, config.getTimeout())));
            String node = node();
            String id = UUID.randomUUID().toString();
            Saved session =
                    new Saved(
                            request.draft() == null ? request.resourceId() : null,
                            json.writeValueAsString(config),
                            config.getProtocol().name(),
                            config.getBasePath(),
                            node);
            store.set(prefix + id, cipher.encrypt(json.writeValueAsString(session)), TTL);
            return new FileBrowseDTO.Session(
                    id, session.protocol(), name, session.root(), node, TTL);
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    public FileBrowseDTO.Page list(
            String id,
            String actor,
            String path,
            String search,
            int page,
            int pageSize,
            boolean refresh) {
        String key = key(id, actor);
        path = relative(path);
        if (search == null || search.length() > 128 || page < 1 || pageSize < 1 || pageSize > 100)
            throw new BusinessException(ResultCode.PARAM_ERROR);
        Saved session = read(key, Saved.class);
        if ("LOCAL".equals(session.protocol()) && !session.node().equals(node()))
            throw new BusinessException(ResultCode.CONFLICT, BrowseMessage.NODE);
        if (session.resourceId() != null) {
            Resource current = resource(session.resourceId());
            try {
                var original = json.readValue(session.config(), FileResourceConfig.class);
                var latest =
                        json.readValue(
                                configs.decryptForRuntime(current), FileResourceConfig.class);
                original.setTimeout(0);
                latest.setTimeout(0);
                if (!original.equals(latest))
                    throw new BusinessException(ResultCode.CONFLICT, BrowseMessage.EXPIRED);
            } catch (java.io.IOException e) {
                throw new BusinessException(ResultCode.CONFLICT, BrowseMessage.EXPIRED);
            }
        }
        Snapshot snapshot = null;
        String cached = store.get(key + ":listing");
        try {
            if (!refresh && cached != null)
                snapshot = json.readValue(cipher.decrypt(cached), Snapshot.class);
            if (snapshot == null || !snapshot.path().equals(path)) {
                if (!slots.tryAcquire())
                    throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE);
                try {
                    Resource temporary = new Resource();
                    temporary.setType(ResourceType.FILE);
                    temporary.setConfig(session.config());
                    List<FileBrowseDTO.Entry> rows = new ArrayList<>();
                    ResourceClientFactory factory =
                            factories.stream()
                                    .filter(f -> f.supportType() == ResourceType.FILE)
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ResultCode.SERVICE_UNAVAILABLE));
                    try (FileClient client = (FileClient) factory.create(temporary)) {
                        if ("LOCAL".equals(session.protocol()) && !client.exists(path))
                            throw new BusinessException(ResultCode.NOT_FOUND);
                        for (var file : client.listBrowsable(path, 5000)) {
                            String name = file.getName();
                            if (name == null || name.equals(".") || name.equals("..")) continue;
                            if (name.length() > 255
                                    || name.contains("/")
                                    || name.contains("\\")
                                    || name.chars().anyMatch(c -> c < 32 || c == 127)) continue;
                            rows.add(
                                    new FileBrowseDTO.Entry(
                                            name,
                                            file.isDirectory(),
                                            file.getSize(),
                                            file.getLastModified() == null
                                                    ? ""
                                                    : file.getLastModified()
                                                            .atZone(
                                                                    java.time.ZoneId
                                                                            .systemDefault())
                                                            .toInstant()
                                                            .toString()));
                        }
                    }
                    rows.sort(
                            Comparator.comparing(FileBrowseDTO.Entry::directory)
                                    .reversed()
                                    .thenComparing(FileBrowseDTO.Entry::name));
                    snapshot = new Snapshot(path, rows);
                    String serialized = json.writeValueAsString(snapshot);
                    if (serialized.getBytes(StandardCharsets.UTF_8).length > 512 * 1024)
                        throw new BusinessException(ResultCode.PARAM_ERROR, BrowseMessage.LIMIT);
                    store.set(
                            key + ":listing",
                            cipher.encrypt(serialized),
                            Math.max(1, store.getTimeout(key)));
                } finally {
                    slots.release();
                }
            }
            String filter = search.toLowerCase(Locale.ROOT);
            var rows =
                    snapshot.entries().stream()
                            .filter(r -> r.name().toLowerCase(Locale.ROOT).contains(filter))
                            .toList();
            long start = (long) (page - 1) * pageSize;
            int from = (int) Math.min(start, rows.size());
            return new FileBrowseDTO.Page(
                    path,
                    rows.subList(from, Math.min(from + pageSize, rows.size())),
                    rows.size(),
                    page,
                    pageSize);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("quota"))
                throw new BusinessException(ResultCode.PARAM_ERROR, BrowseMessage.LIMIT);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, BrowseMessage.FAILED);
        }
    }

    public void close(String id, String actor) {
        String key = key(id, actor);
        store.delete(key + ":listing");
        store.delete(key);
    }

    private Resource resource(Long id) {
        Resource resource = resources.getResource(id);
        if (resource == null
                || resource.getType() != ResourceType.FILE
                || !Boolean.TRUE.equals(resource.getEnabled()))
            throw new BusinessException(ResultCode.NOT_FOUND);
        return resource;
    }

    private <T> T read(String key, Class<T> type) {
        String data = store.get(key);
        if (data == null) throw new BusinessException(ResultCode.CONFLICT, BrowseMessage.EXPIRED);
        try {
            return json.readValue(cipher.decrypt(data), type);
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.CONFLICT, BrowseMessage.EXPIRED);
        }
    }

    public static String relative(String path) {
        if (path == null
                || path.length() > 2048
                || path.contains("${")
                || path.contains("\\")
                || path.contains(":")
                || path.startsWith("//")
                || path.chars().anyMatch(c -> c < 32 || c == 127))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) throw new BusinessException(ResultCode.PARAM_ERROR);
            if (!part.isEmpty() && !part.equals(".")) parts.add(part);
        }
        return String.join("/", parts);
    }

    private String node() {
        String node = properties.getRuntime().getInstanceName();
        return node == null || node.isBlank() ? "local" : node;
    }

    private String key(String id, String actor) {
        if (id == null || !id.matches("[a-f0-9-]{36}"))
            throw new BusinessException(ResultCode.PARAM_ERROR);
        return prefix(actor) + id;
    }

    private String prefix(String actor) {
        try {
            return "ijob:preview:files:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(actor.getBytes(StandardCharsets.UTF_8)))
                    + ":";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum BrowseMessage implements com.ifielsc.ijob.common.response.MessageDefinition {
        EXPIRED("The browser session expired or the resource changed. Open it again."),
        NODE("This local directory belongs to another node. Open the browser on that node."),
        LIMIT("Directory listing exceeds the limit. Choose a smaller directory."),
        FAILED("Cannot list this directory. Check the path, permissions and connection.");
        private final String message;

        BrowseMessage(String message) {
            this.message = message;
        }

        public String errorCode() {
            return "FILE_BROWSE_" + name();
        }

        public String messageKey() {
            return "resource.browse." + name().toLowerCase(Locale.ROOT);
        }

        public String defaultMessage() {
            return message;
        }
    }
}
