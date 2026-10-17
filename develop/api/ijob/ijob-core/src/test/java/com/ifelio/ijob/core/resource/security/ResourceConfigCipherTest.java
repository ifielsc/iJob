package com.ifelio.ijob.core.resource.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.config.AppProperties;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class ResourceConfigCipherTest {
    private static final String PLAINTEXT = "{\"username\":\"demo\",\"password\":\"secret-value\"}";

    @Test
    void shouldRoundTripWithRandomAuthenticatedCiphertext() {
        ResourceConfigCipher cipher = cipher(key((byte) 1), "primary");

        String first = cipher.encrypt(PLAINTEXT);
        String second = cipher.encrypt(PLAINTEXT);

        assertTrue(cipher.isProtected(first));
        assertNotEquals(first, second);
        assertFalse(first.contains("secret-value"));
        assertEquals(PLAINTEXT, cipher.decrypt(first));
        assertEquals(PLAINTEXT, cipher.decrypt(second));
    }

    @Test
    void shouldRejectTamperedCiphertextWithoutLeakingPlaintext() {
        ResourceConfigCipher cipher = cipher(key((byte) 2), "primary");
        String encrypted = cipher.encrypt(PLAINTEXT);
        int ciphertextStart = encrypted.lastIndexOf(':') + 1;
        char replacement = encrypted.charAt(ciphertextStart) == 'A' ? 'B' : 'A';
        String tampered =
                encrypted.substring(0, ciphertextStart)
                        + replacement
                        + encrypted.substring(ciphertextStart + 1);

        ResourceConfigProtectionException exception =
                assertThrows(
                        ResourceConfigProtectionException.class, () -> cipher.decrypt(tampered));

        assertEquals("Resource configuration decryption failed", exception.getMessage());
        assertFalse(exception.toString().contains("secret-value"));
    }

    @Test
    void shouldRejectUnavailableKeyVersion() {
        String encrypted = cipher(key((byte) 3), "primary").encrypt(PLAINTEXT);
        ResourceConfigCipher rotated = cipher(key((byte) 4), "next");

        ResourceConfigProtectionException exception =
                assertThrows(
                        ResourceConfigProtectionException.class, () -> rotated.decrypt(encrypted));

        assertEquals("Resource configuration key is unavailable", exception.getMessage());
        assertFalse(exception.toString().contains("secret-value"));
    }

    @Test
    void shouldRejectWrongKeyWithTheSameKeyId() {
        String encrypted = cipher(key((byte) 6), "primary").encrypt(PLAINTEXT);
        ResourceConfigCipher wrongKey = cipher(key((byte) 7), "primary");

        ResourceConfigProtectionException exception =
                assertThrows(
                        ResourceConfigProtectionException.class, () -> wrongKey.decrypt(encrypted));

        assertEquals("Resource configuration decryption failed", exception.getMessage());
        assertFalse(exception.toString().contains("secret-value"));
    }

    @Test
    void shouldFailClosedWhenKeyIsMissingOrInvalid() {
        ResourceConfigCipher missing = cipher("", "primary");
        ResourceConfigCipher shortKey =
                cipher(Base64.getEncoder().encodeToString(new byte[16]), "primary");

        ResourceConfigProtectionException missingException =
                assertThrows(
                        ResourceConfigProtectionException.class, () -> missing.encrypt(PLAINTEXT));
        ResourceConfigProtectionException shortKeyException =
                assertThrows(
                        ResourceConfigProtectionException.class, () -> shortKey.encrypt(PLAINTEXT));

        assertEquals(
                "Resource configuration encryption is not configured correctly",
                missingException.getMessage());
        assertEquals(
                "Resource configuration encryption is not configured correctly",
                shortKeyException.getMessage());
    }

    @Test
    void shouldRejectMalformedEnvelope() {
        ResourceConfigCipher cipher = cipher(key((byte) 5), "primary");

        ResourceConfigProtectionException exception =
                assertThrows(
                        ResourceConfigProtectionException.class,
                        () -> cipher.decrypt("plain-resource-config"));

        assertEquals("Resource configuration envelope is invalid", exception.getMessage());
    }

    private ResourceConfigCipher cipher(String key, String keyId) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getAes().setKey(key);
        properties.getSecurity().getAes().setKeyId(keyId);
        return new ResourceConfigCipher(properties);
    }

    private String key(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
