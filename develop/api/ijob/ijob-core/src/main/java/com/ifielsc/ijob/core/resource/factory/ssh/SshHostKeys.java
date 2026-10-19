// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.core.resource.factory.ssh;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.core.error.CoreMessage;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.Session;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

public final class SshHostKeys {
    private SshHostKeys() {}

    public static void configureAlgorithms(Session session) {
        // Keep modern algorithms first; match the existing SFTP legacy-server fallbacks.
        session.setConfig("kex", session.getConfig("kex") + ",diffie-hellman-group14-sha1");
        session.setConfig("server_host_key", session.getConfig("server_host_key") + ",ssh-rsa");
    }

    public static String mode(SshResourceConfig config) {
        return config.getHostKeyVerification() == null
                ? "KNOWN_HOSTS"
                : config.getHostKeyVerification();
    }

    public static void configure(
            com.jcraft.jsch.JSch jsch,
            String mode,
            String host,
            int port,
            String hostKey,
            String knownHosts)
            throws com.jcraft.jsch.JSchException {
        if ("MANAGED".equals(mode)) {
            if (!validHost(host))
                throw new BusinessException(
                        ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
            String target = port == 22 ? host : "[" + host + "]:" + port;
            String entry = target + " " + type(hostKey) + " " + hostKey + "\n";
            jsch.setKnownHosts(
                    new java.io.ByteArrayInputStream(entry.getBytes(StandardCharsets.UTF_8)));
        } else if (mode == null || "KNOWN_HOSTS".equals(mode)) {
            if (knownHosts == null || knownHosts.isBlank())
                throw new BusinessException(
                        ResultCode.PARAM_ERROR,
                        CoreMessage.SSH_RESOURCES_REQUIRE_A_KNOWNHOSTS_FILE);
            jsch.setKnownHosts(knownHosts);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
        }
    }

    public static byte[] decode(String value) {
        try {
            if (value == null || value.length() > 16384) throw new IllegalArgumentException();
            byte[] key = Base64.getDecoder().decode(value);
            if (key.length < 16 || key.length > 12288) throw new IllegalArgumentException();
            ByteBuffer fields = ByteBuffer.wrap(key);
            String type = new String(field(fields), StandardCharsets.US_ASCII);
            if (!Set.of(
                            "ssh-ed25519",
                            "ssh-rsa",
                            "ecdsa-sha2-nistp256",
                            "ecdsa-sha2-nistp384",
                            "ecdsa-sha2-nistp521")
                    .contains(type)) throw new IllegalArgumentException();
            int count = type.equals("ssh-ed25519") ? 1 : 2;
            for (int i = 0; i < count; i++) {
                byte[] part = field(fields);
                if (part.length == 0 || type.equals("ssh-ed25519") && part.length != 32)
                    throw new IllegalArgumentException();
            }
            if (fields.hasRemaining() || !new HostKey("probe", key).getType().equals(type))
                throw new IllegalArgumentException();
            return key;
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
        }
    }

    private static byte[] field(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) throw new IllegalArgumentException();
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }

    public static String fingerprint(String value) {
        try {
            return "SHA256:"
                    + Base64.getEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    MessageDigest.getInstance("SHA-256").digest(decode(value)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String type(String value) {
        try {
            return new HostKey("probe", decode(value)).getType();
        } catch (com.jcraft.jsch.JSchException exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR, CoreMessage.SSH_HOST_KEY_INVALID);
        }
    }

    public static boolean validHost(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 255
                && value.chars()
                        .noneMatch(
                                c ->
                                        Character.isWhitespace(c)
                                                || Character.isISOControl(c)
                                                || ",[]*?!|/\\".indexOf(c) >= 0);
    }
}
