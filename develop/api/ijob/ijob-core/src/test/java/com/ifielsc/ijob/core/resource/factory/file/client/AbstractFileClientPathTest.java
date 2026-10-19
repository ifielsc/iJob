// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

class AbstractFileClientPathTest {
    private final ExposedClient client = new ExposedClient("/safe/root");

    @Test
    void shouldResolveRelativeAndAlreadyRootedPathsWithinResourceRoot() {
        assertEquals("/safe/root", client.exposeResolve("/"));
        assertEquals("/safe/root", client.exposeResolve(""));
        assertEquals("/", new ExposedClient("/").exposeResolve("/"));
        assertEquals("/report.csv", new ExposedClient("/").exposeResolve("report.csv"));
        assertEquals("/safe/root/data/file.txt", client.exposeResolve("data//./file.txt"));
        assertEquals("/safe/root/data/file.txt", client.exposeResolve("/safe/root/data/file.txt"));
        assertEquals("/safe/root/rooted", client.exposeResolve("/rooted"));
    }

    @Test
    void shouldRejectTraversalDriveAndUncPaths() {
        assertThrows(SecurityException.class, () -> client.exposeResolve("../outside"));
        assertThrows(
                SecurityException.class, () -> client.exposeResolve("/safe/root/../../outside"));
        assertThrows(SecurityException.class, () -> client.exposeResolve("C:/outside"));
        assertThrows(SecurityException.class, () -> client.exposeResolve("//server/share"));
    }

    @Test
    void childCheckShouldUseSegmentBoundary() {
        assertTrue(client.exposeIsChild("/safe/root", "/safe/root/a"));
        assertTrue(client.exposeIsChild("/safe/root", "/safe/root"));
        assertFalse(client.exposeIsChild("/safe/root", "/safe/rooted"));
    }

    private static final class ExposedClient extends AbstractFileClient {
        private ExposedClient(String rootPath) {
            super(rootPath);
        }

        private String exposeResolve(String path) {
            return resolve(path);
        }

        private boolean exposeIsChild(String parent, String child) {
            return isChild(parent, child);
        }

        @Override
        public List<FileInfo> list(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream download(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upload(String path, InputStream input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void mkdirs(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String path) {
            return false;
        }

        @Override
        public void delete(String path) {
            throw new UnsupportedOperationException();
        }
    }
}
