package com.ifielsc.ijob.core.notify.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifielsc.ijob.common.config.AppProperties;
import com.ifielsc.ijob.core.resource.security.ResourceConfigCipher;
import com.ifielsc.ijob.core.resource.security.ResourceConfigProtectionException;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

class NotifyConfigCipherTest {
    private static final String PLAINTEXT =
            """
            {"username":"mail-user","password":"mail-password",\
            "to":["operator@example.com"],"webhook":"secret-url"}
            """;

    @Test
    void shouldRoundTripWithRandomAuthenticatedCiphertext() {
        NotifyConfigCipher cipher = cipher(key((byte) 1), "primary");

        String first = cipher.encrypt(PLAINTEXT);
        String second = cipher.encrypt(PLAINTEXT);

        assertTrue(cipher.isProtected(first));
        assertNotEquals(first, second);
        assertFalse(first.contains("mail-password"));
        assertFalse(first.contains("operator@example.com"));
        assertEquals(PLAINTEXT, cipher.decrypt(first));
        assertEquals(PLAINTEXT, cipher.decrypt(second));
    }

    @Test
    void shouldRejectTamperedCiphertextWithoutLeakingPlaintext() {
        NotifyConfigCipher cipher = cipher(key((byte) 2), "primary");
        String encrypted = cipher.encrypt(PLAINTEXT);
        int ciphertextStart = encrypted.lastIndexOf(':') + 1;
        char replacement = encrypted.charAt(ciphertextStart) == 'A' ? 'B' : 'A';
        String tampered =
                encrypted.substring(0, ciphertextStart)
                        + replacement
                        + encrypted.substring(ciphertextStart + 1);

        NotifyConfigProtectionException exception =
                assertThrows(NotifyConfigProtectionException.class, () -> cipher.decrypt(tampered));

        assertEquals("Notification configuration decryption failed", exception.getMessage());
        assertFalse(exception.toString().contains("mail-password"));
    }

    @Test
    void shouldRejectUnavailableKeyVersionAndWrongKey() {
        String encrypted = cipher(key((byte) 3), "primary").encrypt(PLAINTEXT);

        NotifyConfigProtectionException unavailable =
                assertThrows(
                        NotifyConfigProtectionException.class,
                        () -> cipher(key((byte) 4), "next").decrypt(encrypted));
        NotifyConfigProtectionException wrongKey =
                assertThrows(
                        NotifyConfigProtectionException.class,
                        () -> cipher(key((byte) 5), "primary").decrypt(encrypted));

        assertEquals("Notification configuration key is unavailable", unavailable.getMessage());
        assertEquals("Notification configuration decryption failed", wrongKey.getMessage());
    }

    @Test
    void shouldFailClosedWhenKeyIsMissingOrInvalid() {
        NotifyConfigCipher missing = cipher("", "primary");
        NotifyConfigCipher shortKey =
                cipher(Base64.getEncoder().encodeToString(new byte[16]), "primary");

        NotifyConfigProtectionException missingException =
                assertThrows(
                        NotifyConfigProtectionException.class, () -> missing.encrypt(PLAINTEXT));
        NotifyConfigProtectionException shortKeyException =
                assertThrows(
                        NotifyConfigProtectionException.class, () -> shortKey.encrypt(PLAINTEXT));

        assertEquals(
                "Notification configuration encryption is not configured correctly",
                missingException.getMessage());
        assertEquals(
                "Notification configuration encryption is not configured correctly",
                shortKeyException.getMessage());
    }

    @Test
    void shouldRejectMalformedEnvelope() {
        NotifyConfigCipher cipher = cipher(key((byte) 6), "primary");

        NotifyConfigProtectionException exception =
                assertThrows(
                        NotifyConfigProtectionException.class,
                        () -> cipher.decrypt("plain-notify-config"));

        assertEquals("Notification configuration envelope is invalid", exception.getMessage());
    }

    @Test
    void shouldIsolateNotifyAndResourceCiphertextByAadPurpose() {
        AppProperties properties = properties(key((byte) 7), "primary");
        NotifyConfigCipher notifyCipher = new NotifyConfigCipher(properties);
        ResourceConfigCipher resourceCipher = new ResourceConfigCipher(properties);

        String notifyCiphertext = notifyCipher.encrypt(PLAINTEXT);
        String resourceCiphertext = resourceCipher.encrypt(PLAINTEXT);

        ResourceConfigProtectionException resourceException =
                assertThrows(
                        ResourceConfigProtectionException.class,
                        () -> resourceCipher.decrypt(notifyCiphertext));
        NotifyConfigProtectionException notifyException =
                assertThrows(
                        NotifyConfigProtectionException.class,
                        () -> notifyCipher.decrypt(resourceCiphertext));

        assertEquals("Resource configuration decryption failed", resourceException.getMessage());
        assertEquals("Notification configuration decryption failed", notifyException.getMessage());
    }

    private NotifyConfigCipher cipher(String key, String keyId) {
        return new NotifyConfigCipher(properties(key, keyId));
    }

    private AppProperties properties(String key, String keyId) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getAes().setKey(key);
        properties.getSecurity().getAes().setKeyId(keyId);
        return properties;
    }

    private String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
