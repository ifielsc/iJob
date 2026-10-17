package com.ifelio.ijob.core.security;

import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.DECRYPTION_FAILED;
import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.ENCRYPTION_FAILED;
import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.INVALID_CONFIGURATION;
import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.INVALID_ENVELOPE;
import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.KEY_UNAVAILABLE;
import static com.ifelio.ijob.core.security.ConfigProtectionException.Reason.PLAINTEXT_REQUIRED;

import com.ifelio.ijob.common.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmConfigProtector {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String PREFIX = "ifjenc:v1:";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE;
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final AppProperties properties;
    private final byte[] aadPrefix;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmConfigProtector(AppProperties properties, String aadPurpose) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        if (aadPurpose == null || aadPurpose.isBlank()) {
            throw new IllegalArgumentException("aadPurpose must not be blank");
        }
        this.aadPrefix = (aadPurpose + ":").getBytes(StandardCharsets.UTF_8);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new ConfigProtectionException(PLAINTEXT_REQUIRED);
        }

        KeyMaterial keyMaterial = loadKeyMaterial();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    keyMaterial.key(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad(keyMaterial.keyId()));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return PREFIX
                    + keyMaterial.keyId()
                    + ":"
                    + encoder.encodeToString(iv)
                    + ":"
                    + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new ConfigProtectionException(ENCRYPTION_FAILED, e);
        }
    }

    public String decrypt(String protectedValue) {
        ProtectedConfig protectedConfig = parse(protectedValue);
        KeyMaterial keyMaterial = loadKeyMaterial();
        if (!keyMaterial.keyId().equals(protectedConfig.keyId())) {
            throw new ConfigProtectionException(KEY_UNAVAILABLE);
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyMaterial.key(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, protectedConfig.iv()));
            cipher.updateAAD(aad(protectedConfig.keyId()));
            byte[] plaintext = cipher.doFinal(protectedConfig.ciphertext());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new ConfigProtectionException(DECRYPTION_FAILED, e);
        }
    }

    public boolean isProtected(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private ProtectedConfig parse(String protectedValue) {
        if (protectedValue == null) {
            throw new ConfigProtectionException(INVALID_ENVELOPE);
        }

        String[] parts = protectedValue.split(":", 5);
        if (parts.length != 5
                || !"ifjenc".equals(parts[0])
                || !"v1".equals(parts[1])
                || !validKeyId(parts[2])) {
            throw new ConfigProtectionException(INVALID_ENVELOPE);
        }

        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[3]);
            byte[] ciphertext = decoder.decode(parts[4]);
            if (iv.length != IV_LENGTH_BYTES || ciphertext.length < TAG_LENGTH_BYTES) {
                throw new ConfigProtectionException(INVALID_ENVELOPE);
            }
            return new ProtectedConfig(parts[2], iv, ciphertext);
        } catch (IllegalArgumentException e) {
            throw new ConfigProtectionException(INVALID_ENVELOPE, e);
        }
    }

    private KeyMaterial loadKeyMaterial() {
        AppProperties.Security security = properties.getSecurity();
        AppProperties.Security.Aes aes = security == null ? null : security.getAes();
        if (aes == null
                || !validKeyId(aes.getKeyId())
                || aes.getKey() == null
                || aes.getKey().isBlank()) {
            throw new ConfigProtectionException(INVALID_CONFIGURATION);
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(aes.getKey());
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new ConfigProtectionException(INVALID_CONFIGURATION);
            }
            return new KeyMaterial(aes.getKeyId(), new SecretKeySpec(keyBytes, "AES"));
        } catch (IllegalArgumentException e) {
            throw new ConfigProtectionException(INVALID_CONFIGURATION, e);
        }
    }

    private boolean validKeyId(String keyId) {
        return keyId != null && KEY_ID_PATTERN.matcher(keyId).matches();
    }

    private byte[] aad(String keyId) {
        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
        byte[] aad = new byte[aadPrefix.length + keyIdBytes.length];
        System.arraycopy(aadPrefix, 0, aad, 0, aadPrefix.length);
        System.arraycopy(keyIdBytes, 0, aad, aadPrefix.length, keyIdBytes.length);
        return aad;
    }

    private record KeyMaterial(String keyId, SecretKey key) {}

    private record ProtectedConfig(String keyId, byte[] iv, byte[] ciphertext) {}
}
