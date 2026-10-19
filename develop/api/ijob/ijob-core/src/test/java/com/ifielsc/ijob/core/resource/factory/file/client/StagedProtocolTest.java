// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class StagedProtocolTest {
    private static final String TEMP = ".ijob-transfer-123e4567-e89b-12d3-a456-426614174000.part";

    @Test
    void ftpUsesRenameAndOnlyExactTemporaryDelete() throws Exception {
        List<String> operations = new ArrayList<>();
        var ftp =
                new FTPClient() {
                    @Override
                    public boolean rename(String from, String to) {
                        operations.add(from + " -> " + to);
                        return true;
                    }

                    @Override
                    public boolean deleteFile(String path) {
                        operations.add(path);
                        return true;
                    }
                };
        var client = new FtpFileClient(ftp, "/root");
        client.renameFile(TEMP, "data.csv");
        client.deleteTemporaryFile(TEMP);
        assertEquals(List.of("/root/" + TEMP + " -> /root/data.csv", "/root/" + TEMP), operations);
        assertThrows(IOException.class, () -> client.deleteTemporaryFile("data.csv"));
        assertThrows(SecurityException.class, () -> client.renameFile(TEMP, "../escape"));
    }

    @Test
    void ftpDistinguishesRejectedAndUnknownRename() {
        var rejected =
                new FtpFileClient(
                        new FTPClient() {
                            @Override
                            public boolean rename(String from, String to) {
                                return false;
                            }
                        },
                        "/root");
        assertEquals(
                IOException.class,
                assertThrows(IOException.class, () -> rejected.renameFile(TEMP, "data.csv"))
                        .getClass());
        var uncertain =
                new FtpFileClient(
                        new FTPClient() {
                            @Override
                            public boolean rename(String from, String to) throws IOException {
                                throw new IOException("lost reply");
                            }
                        },
                        "/root");
        assertThrows(
                FilePublicationUncertainException.class,
                () -> uncertain.renameFile(TEMP, "data.csv"));
    }

    @Test
    void sftpUsesRenameAndOnlyExactTemporaryRemove() throws Exception {
        var channel = new RecordingSftp();
        var client = new SftpFileClient(channel, "/root");
        client.renameFile(TEMP, "data.csv");
        client.deleteTemporaryFile(TEMP);
        assertEquals(
                List.of("/root/" + TEMP + " -> /root/data.csv", "/root/" + TEMP),
                channel.operations);
        assertThrows(IOException.class, () -> client.deleteTemporaryFile("data.csv"));
        channel.failure = ChannelSftp.SSH_FX_PERMISSION_DENIED;
        assertEquals(
                IOException.class,
                assertThrows(IOException.class, () -> client.renameFile(TEMP, "data.csv"))
                        .getClass());
        channel.failure = ChannelSftp.SSH_FX_CONNECTION_LOST;
        assertThrows(
                FilePublicationUncertainException.class, () -> client.renameFile(TEMP, "data.csv"));
    }

    @Test
    void sftpRenameAndCleanupRejectLinkedParent() {
        var channel = new RecordingSftp();
        channel.link = true;
        var client = new SftpFileClient(channel, "/root");
        assertThrows(SecurityException.class, () -> client.renameFile("link/" + TEMP, "data.csv"));
        assertThrows(SecurityException.class, () -> client.renameFile(TEMP, "link/data.csv"));
        assertThrows(SecurityException.class, () -> client.deleteTemporaryFile("link/" + TEMP));
        assertTrue(channel.operations.isEmpty());
    }

    private static class RecordingSftp extends ChannelSftp {
        List<String> operations = new ArrayList<>();
        int failure;
        boolean link;

        @Override
        public SftpATTRS lstat(String path) {
            try {
                var ctor = SftpATTRS.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                var attrs = ctor.newInstance();
                var permissions = SftpATTRS.class.getDeclaredField("permissions");
                permissions.setAccessible(true);
                permissions.setInt(attrs, link && path.equals("/root/link") ? 0120777 : 0040755);
                var flags = SftpATTRS.class.getDeclaredField("flags");
                flags.setAccessible(true);
                flags.setInt(attrs, 4);
                return attrs;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void rename(String from, String to) throws SftpException {
            if (failure != 0) throw new SftpException(failure, "rename failed");
            operations.add(from + " -> " + to);
        }

        @Override
        public void rm(String path) {
            operations.add(path);
        }
    }
}
