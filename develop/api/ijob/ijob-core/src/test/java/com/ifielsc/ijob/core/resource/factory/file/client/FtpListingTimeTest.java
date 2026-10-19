package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.net.ftp.FTPCmd;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

class FtpListingTimeTest {
    @Test
    void machineListingPreservesUtcModificationTime() throws Exception {
        WireListing client =
                new WireListing(
                        true, "type=file;size=63;modify=20260930183954.612; sample.csv\r\n");
        var files = client.listBounded("/in", 10);
        assertEquals(FTPCmd.MLSD, client.command);
        assertEquals("sample.csv", files[0].getName());
        assertEquals(
                Instant.parse("2026-09-30T18:39:54.612Z"), files[0].getTimestamp().toInstant());
    }

    @Test
    void legacyServerKeepsListFallback() throws Exception {
        WireListing client =
                new WireListing(false, "-rw-r--r-- 1 user group 63 Sep 30 18:39 sample.csv\r\n");
        assertEquals("sample.csv", client.listBounded("/in", 10)[0].getName());
        assertEquals(FTPCmd.LIST, client.command);
    }

    @Test
    void machineListingStillEnforcesEntryQuota() {
        WireListing client =
                new WireListing(true, "type=file;size=1; a\r\ntype=file;size=1; b\r\n");
        assertThrows(IOException.class, () -> client.listBounded("/in", 1));
    }

    private static class WireListing extends FtpFileClient.BoundedListingClient {
        final boolean machine;
        final byte[] bytes;
        FTPCmd command;

        WireListing(boolean machine, String text) {
            this.machine = machine;
            this.bytes = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean hasFeature(String name) {
            return machine;
        }

        @Override
        public String getSystemType() {
            return "UNIX";
        }

        @Override
        public boolean completePendingCommand() {
            return true;
        }

        @Override
        protected Socket _openDataConnection_(FTPCmd command, String path) {
            this.command = command;
            return new Socket() {
                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(bytes);
                }
            };
        }
    }
}
