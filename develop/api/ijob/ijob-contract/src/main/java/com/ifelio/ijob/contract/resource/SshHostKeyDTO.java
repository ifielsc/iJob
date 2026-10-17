package com.ifelio.ijob.contract.resource;

/** Public host identity observed in a verified SSH key exchange, before authentication. */
public record SshHostKeyDTO(
        String host, int port, String keyType, String fingerprint, String publicKey) {}
