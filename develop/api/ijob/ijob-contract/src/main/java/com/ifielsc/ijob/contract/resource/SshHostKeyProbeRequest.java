package com.ifielsc.ijob.contract.resource;

/** Probe deliberately has no account or credential fields. */
public record SshHostKeyProbeRequest(String host, Integer port, Integer connectTimeout) {}
