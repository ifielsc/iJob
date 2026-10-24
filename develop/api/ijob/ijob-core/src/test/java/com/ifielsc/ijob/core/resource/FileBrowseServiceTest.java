// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.common.cluster.ClusterStateStore;
import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.enums.ResourceType;
import com.ifielsc.ijob.contract.resource.FileBrowseDTO;
import com.ifielsc.ijob.contract.resource.ResourceConfigRequest;
import com.ifielsc.ijob.contract.resource.ResourceUpsertRequest;
import com.ifielsc.ijob.core.resource.factory.file.FileResourceFactory;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.infra.entity.resource.Resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class FileBrowseServiceTest {
    @TempDir Path directory;
    private final Map<String, String> states = new ConcurrentHashMap<>();
    private final AppProperties properties = new AppProperties();
    private Resource saved;
    private ResourceConfigService configs;

    private FileBrowseService service() {
        properties.getSecurity().getAes().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.getRuntime().setInstanceName("browser-a");
        var cipher = new ResourceConfigCipher(properties);
        configs = new ResourceConfigService(cipher);
        var resources =
                new ResourceService(null, configs, null, null, null) {
                    @Override
                    public Resource getResource(Long id) {
                        return saved;
                    }
                };
        var store =
                (ClusterStateStore)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ClusterStateStore.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "get" -> states.get(args[0]);
                                            case "set" -> {
                                                states.put((String) args[0], (String) args[1]);
                                                yield null;
                                            }
                                            case "delete" -> {
                                                states.remove(args[0]);
                                                yield null;
                                            }
                                            case "getTimeout" -> 120L;
                                            case "search" ->
                                                    states.keySet().stream()
                                                            .filter(
                                                                    k ->
                                                                            k.startsWith(
                                                                                    (String)
                                                                                            args[
                                                                                                    0]))
                                                            .toList();
                                            default -> throw new AssertionError(method.getName());
                                        });
        return new FileBrowseService(
                resources,
                configs,
                cipher,
                List.of(new FileResourceFactory()),
                store,
                properties,
                new ObjectMapper());
    }

    private ResourceUpsertRequest draft() {
        var request = new ResourceUpsertRequest();
        request.setType(ResourceType.FILE);
        request.setName("Browser fixture");
        var file = new ResourceConfigRequest.File();
        file.setProtocol("LOCAL");
        file.setBasePath(directory.toString());
        request.setFileConfig(file);
        return request;
    }

    @Test
    void modificationTimeIsAnExplicitUtcInstantEvenWithANonUtcHost() throws Exception {
        var previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"));
            var browser = service();
            properties.getResourceBrowser().setLocalRoots(List.of(directory.toString()));
            var file = Files.writeString(directory.resolve("time.txt"), "unchanged");
            var instant = java.time.Instant.parse("2026-10-22T00:00:00Z");
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(instant));
            var session = browser.open(new FileBrowseDTO.Request(null, draft(), "0"), "admin");
            assertEquals(
                    instant.toString(),
                    browser.list(session.id(), "admin", "", "", 1, 50, false)
                            .entries()
                            .getFirst()
                            .modified());
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    void newLocalResourcesRequireAnAllowedRootAndNeverBrowseArbitraryPaths() {
        var browser = service();
        assertThrows(
                BusinessException.class,
                () -> browser.open(new FileBrowseDTO.Request(null, draft(), "0"), "admin"));
        properties.getResourceBrowser().setLocalRoots(List.of(directory.toString()));
        var session = browser.open(new FileBrowseDTO.Request(null, draft(), "0"), "admin");
        assertEquals(directory.toString(), session.root());
        assertThrows(
                BusinessException.class,
                () -> browser.list(session.id(), "other", "", "", 1, 50, false));
        for (String path :
                List.of("../", "inbox/../", "//host/share", "C:/data", "a\\b", "${path}", "a\0b"))
            assertThrows(
                    BusinessException.class,
                    () -> browser.list(session.id(), "admin", path, "", 1, 50, false),
                    path);
        browser.close(session.id(), "admin");
        assertTrue(states.isEmpty());
    }

    @Test
    void listingsAreEncryptedBoundedSnapshotsAndDoNotModifyFiles() throws Exception {
        var browser = service();
        Files.createDirectory(directory.resolve("inbox"));
        Files.writeString(directory.resolve("orders.csv"), "unchanged");
        saved = new Resource();
        saved.setId(1L);
        saved.setType(ResourceType.FILE);
        saved.setEnabled(true);
        saved.setName("Saved files");
        saved.setConfig(configs.protect(draft(), null));
        var session = browser.open(new FileBrowseDTO.Request(1L, null, null), "admin");
        var first = browser.list(session.id(), "admin", "", "", 1, 1, false);
        assertEquals(2, first.total());
        assertEquals("inbox", first.entries().getFirst().name());
        Files.writeString(directory.resolve("later.csv"), "later");
        assertEquals(2, browser.list(session.id(), "admin", "", "", 2, 1, false).total());
        assertEquals(3, browser.list(session.id(), "admin", "", "", 1, 50, true).total());
        assertEquals(1, browser.list(session.id(), "admin", "", "orders", 1, 50, false).total());
        assertEquals("unchanged", Files.readString(directory.resolve("orders.csv")));
        assertFalse(
                states.values().stream()
                        .anyMatch(
                                v -> v.contains("orders.csv") || v.contains(directory.toString())));
        saved.setEnabled(false);
        assertThrows(
                BusinessException.class,
                () -> browser.list(session.id(), "admin", "", "", 1, 50, false));
    }

    @Test
    void localSessionCannotSilentlyBrowseAnotherNode() {
        var browser = service();
        properties.getResourceBrowser().setLocalRoots(List.of(directory.toString()));
        var session = browser.open(new FileBrowseDTO.Request(null, draft(), "0"), "admin");
        properties.getRuntime().setInstanceName("browser-b");
        assertEquals(
                "FILE_BROWSE_NODE",
                assertThrows(
                                BusinessException.class,
                                () -> browser.list(session.id(), "admin", "", "", 1, 50, false))
                        .getErrorCode());
    }
}
