// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import io.micrometer.common.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;

public abstract class AbstractFileClient implements FileClient {
    protected final String rootPath;

    protected AbstractFileClient(String rootPath) {
        this.rootPath = normalizeRoot(rootPath);
    }

    public String getRootPath() {
        return rootPath;
    }

    /** Resolves a resource-relative path against the configured root. */
    protected String resolve(String path) {
        if (StringUtils.isBlank(path)) {
            return rootPath;
        }
        String raw = path.replace('\\', '/');
        rejectInvalidPath(raw);
        boolean alreadyUnderRoot =
                "/".equals(rootPath) || raw.equals(rootPath) || raw.startsWith(rootPath + "/");
        String candidate = alreadyUnderRoot ? raw : rootPath + "/" + stripLeadingSlash(raw);
        String normalized = normalizePath(candidate);
        if (!isWithinRoot(normalized)) {
            throw new SecurityException("Illegal resource path");
        }
        return normalized;
    }

    protected final String normalizePath(String path) {
        if (StringUtils.isBlank(path)) {
            return "/";
        }

        path = path.replace('\\', '/');
        rejectInvalidPath(path);
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/+")) {
            if (segment.isBlank() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new SecurityException("Illegal resource path");
                }
                segments.removeLast();
            } else {
                segments.addLast(segment);
            }
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }

    protected final String normalizeRoot(String root) {
        if (StringUtils.isBlank(root)) {
            return "/";
        }
        return normalizePath(root);
    }

    protected String parent(String path) {
        path = normalizePath(path);
        if ("/".equals(path)) {
            return "/";
        }

        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return path.substring(0, index);
    }

    protected String fileName(String path) {
        path = normalizePath(path);
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return path;
        }
        return path.substring(index + 1);
    }

    protected String extension(String path) {
        String name = fileName(path);
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return name.substring(index + 1);
    }

    protected String baseName(String path) {
        String name = fileName(path);
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return name;
        }
        return name.substring(0, index);
    }

    protected String buildPath(String parent, String child) {
        if (StringUtils.isBlank(parent) || "/".equals(parent)) {
            return normalizePath(child);
        }
        if (StringUtils.isBlank(child)) {
            return normalizePath(parent);
        }
        return normalizePath(parent + "/" + child);
    }

    protected boolean isAbsolute(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }

        path = path.replace('\\', '/');
        return path.startsWith("/") || path.matches("^[A-Za-z]:.*");
    }

    protected boolean isChild(String parent, String child) {
        parent = normalizePath(parent);
        child = normalizePath(child);
        return "/".equals(parent) || child.equals(parent) || child.startsWith(parent + "/");
    }

    private boolean isWithinRoot(String path) {
        return "/".equals(rootPath) || path.equals(rootPath) || path.startsWith(rootPath + "/");
    }

    private String stripLeadingSlash(String path) {
        int index = 0;
        while (index < path.length() && path.charAt(index) == '/') {
            index++;
        }
        return path.substring(index);
    }

    private void rejectInvalidPath(String path) {
        if (path.indexOf('\0') >= 0 || path.matches("^[A-Za-z]:.*") || path.startsWith("//")) {
            throw new SecurityException("Illegal resource path");
        }
    }

    @Override
    public void close() {
        FileClient.super.close();
    }
}
