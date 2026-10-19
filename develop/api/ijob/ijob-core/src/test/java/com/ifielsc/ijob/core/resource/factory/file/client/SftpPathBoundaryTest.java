package com.ifielsc.ijob.core.resource.factory.file.client;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

class SftpPathBoundaryTest {
    @Test
    void everyOperationRejectsLinkedParentBeforeFollowingIt() throws Exception {
        ChannelSftp channel =
                new ChannelSftp() {
                    @Override
                    public SftpATTRS lstat(String path) {
                        return attrs(path.equals("/root/link") ? 0120777 : 0040755);
                    }
                };
        SftpFileClient client = new SftpFileClient(channel, "/root");
        assertThrows(SecurityException.class, () -> client.list("link"));
        assertThrows(SecurityException.class, () -> client.download("link/file"));
        assertThrows(
                SecurityException.class,
                () -> client.upload("link/file", new ByteArrayInputStream(new byte[0])));
        assertThrows(SecurityException.class, () -> client.mkdirs("link/new"));
        assertThrows(SecurityException.class, () -> client.exists("link/file"));
        assertThrows(SecurityException.class, () -> client.delete("link/file"));
    }

    @Test
    void deniedParentCannotBeMistakenForMissingDirectory() {
        ChannelSftp channel =
                new ChannelSftp() {
                    @Override
                    public SftpATTRS lstat(String path) throws SftpException {
                        throw new SftpException(SSH_FX_PERMISSION_DENIED, "denied");
                    }

                    @Override
                    public void mkdir(String path) {
                        fail("Must not create through denied parent");
                    }
                };
        assertThrows(
                java.io.IOException.class,
                () -> new SftpFileClient(channel, "/root").mkdirs("new"));
    }

    private static SftpATTRS attrs(int permissions) {
        try {
            var constructor = SftpATTRS.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            SftpATTRS result = constructor.newInstance();
            var field = SftpATTRS.class.getDeclaredField("permissions");
            field.setAccessible(true);
            field.setInt(result, permissions);
            var flags = SftpATTRS.class.getDeclaredField("flags");
            flags.setAccessible(true);
            flags.setInt(result, 4);
            return result;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
