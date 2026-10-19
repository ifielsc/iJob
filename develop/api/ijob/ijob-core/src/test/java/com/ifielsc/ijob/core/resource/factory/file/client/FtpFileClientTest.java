// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FtpFileClientTest {
    @Test
    void existsShouldRecognizeEmptyDirectory() throws Exception {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.directories.add("/empty");

        assertTrue(new FtpFileClient(ftp, "/").exists("/empty"));
        assertEquals("/", ftp.workingDirectory);
    }

    @Test
    void deleteShouldTryFileCommandBeforeDirectoryTraversal() throws Exception {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.files.add("/in/data.csv");

        new FtpFileClient(ftp, "/").delete("/in/data.csv");

        assertEquals(List.of("/in/data.csv"), ftp.deletedFiles);
        assertTrue(ftp.removedDirectories.isEmpty());
        assertTrue(ftp.listedPaths.isEmpty());
    }

    @Test
    void deleteShouldRemoveEmptyDirectoryAfterFileDeleteIsRejected() throws Exception {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.directories.add("/empty");
        ftp.listings.put("/empty", new FTPFile[0]);

        new FtpFileClient(ftp, "/").delete("/empty");

        assertEquals(List.of("/empty"), ftp.removedDirectories);
        assertEquals("/", ftp.workingDirectory);
    }

    @Test
    void mkdirsShouldRejectExistingFileAsDirectory() {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.files.add("/blocked");
        ftp.listings.put("/blocked", new FTPFile[] {file("blocked")});

        IOException exception =
                assertThrows(
                        IOException.class,
                        () -> new FtpFileClient(ftp, "/").mkdirs("/blocked/child"));

        assertEquals("path is not a directory:/blocked", exception.getMessage());
        assertTrue(ftp.createdDirectories.isEmpty());
    }

    @Test
    void deleteShouldExposeRejectedProtocolOperation() {
        RecordingFtpClient ftp = new RecordingFtpClient();

        IOException exception =
                assertThrows(
                        IOException.class, () -> new FtpFileClient(ftp, "/").delete("/missing"));

        assertEquals("FTP delete failed:/missing", exception.getMessage());
    }

    @Test
    void listShouldExcludeDotDirectories() throws Exception {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.listings.put("/in", new FTPFile[] {directory("."), directory(".."), file("ready.csv")});

        List<FileInfo> result = new FtpFileClient(ftp, "/").list("/in");

        assertEquals(1, result.size());
        assertEquals("ready.csv", result.getFirst().getName());
    }

    @Test
    void rejectedListShouldExposePermanentReply() {
        RecordingFtpClient ftp = new RecordingFtpClient();
        ftp.replyCode = 550;

        FileClientException exception =
                assertThrows(
                        FileClientException.class,
                        () -> new FtpFileClient(ftp, "/").list("/forbidden"));

        assertFalse(exception.isRetryable());
    }

    private static FTPFile file(String name) {
        FTPFile file = new FTPFile();
        file.setName(name);
        file.setType(FTPFile.FILE_TYPE);
        return file;
    }

    private static FTPFile directory(String name) {
        FTPFile file = new FTPFile();
        file.setName(name);
        file.setType(FTPFile.DIRECTORY_TYPE);
        return file;
    }

    private static final class RecordingFtpClient extends FTPClient {
        private final Set<String> files = new HashSet<>();
        private final Set<String> directories = new HashSet<>(Set.of("/"));
        private final Map<String, FTPFile[]> listings = new HashMap<>();
        private final List<String> deletedFiles = new ArrayList<>();
        private final List<String> removedDirectories = new ArrayList<>();
        private final List<String> createdDirectories = new ArrayList<>();
        private final List<String> listedPaths = new ArrayList<>();
        private String workingDirectory = "/";
        private int replyCode = 226;

        @Override
        public String printWorkingDirectory() {
            return workingDirectory;
        }

        @Override
        public boolean changeWorkingDirectory(String path) {
            if (!directories.contains(path)) {
                return false;
            }
            workingDirectory = path;
            return true;
        }

        @Override
        public FTPFile[] listFiles(String path) {
            listedPaths.add(path);
            return listings.getOrDefault(path, new FTPFile[0]);
        }

        @Override
        public int getReplyCode() {
            return replyCode;
        }

        @Override
        public boolean deleteFile(String path) {
            deletedFiles.add(path);
            return files.remove(path);
        }

        @Override
        public boolean removeDirectory(String path) {
            removedDirectories.add(path);
            return directories.remove(path);
        }

        @Override
        public boolean makeDirectory(String path) {
            createdDirectories.add(path);
            return directories.add(path);
        }
    }
}
