package com.ifielsc.ijob.core.resource.factory.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.contract.resource.SshHostKeyProbeRequest;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class SshHostKeysTest {
    public static String sampleKey(byte value) {
        byte[] type = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, value);
        return Base64.getEncoder()
                .encodeToString(
                        ByteBuffer.allocate(4 + type.length + 4 + raw.length)
                                .putInt(type.length)
                                .put(type)
                                .putInt(raw.length)
                                .put(raw)
                                .array());
    }

    @Test
    void fingerprintIsStableAndCoversTheCompleteKeyBlob() {
        String first = sampleKey((byte) 1);
        assertEquals("ssh-ed25519", SshHostKeys.type(first));
        assertTrue(SshHostKeys.fingerprint(first).matches("SHA256:[A-Za-z0-9+/]{43}"));
        assertNotEquals(
                SshHostKeys.fingerprint(first), SshHostKeys.fingerprint(sampleKey((byte) 2)));
        assertEquals(Base64.getDecoder().decode(first).length, SshHostKeys.decode(first).length);
    }

    @Test
    void malformedKeysAndUnsafeHostPatternsAreRejected() {
        for (String key :
                new String[] {"", "not-base64", "A".repeat(20000), sampleKey((byte) 1) + "AAAA"})
            assertThrows(BusinessException.class, () -> SshHostKeys.decode(key));
        for (String host : new String[] {"", "host\nother", "host,other", "*", "[host]", "a/b"})
            assertFalse(SshHostKeys.validHost(host));
        assertTrue(SshHostKeys.validHost("2001:db8::1"));
        assertTrue(SshHostKeys.validHost("runner.internal"));
    }

    @Test
    void invalidProbeInputFailsBeforeNetworking() {
        var service = new SshHostKeyProbeService();
        assertThrows(BusinessException.class, () -> service.probe(null));
        assertThrows(
                BusinessException.class,
                () -> service.probe(new SshHostKeyProbeRequest("localhost", 0, 1000)));
        assertThrows(
                BusinessException.class,
                () -> service.probe(new SshHostKeyProbeRequest("bad\nhost", 22, 1000)));
    }
}
