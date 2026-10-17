package com.ifelio.ijob.core.resource.factory.ssh;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = {"host", "username", "password", "privateKey", "passphrase", "knownHosts"})
public class SshResourceConfig {
    private String hostKeyVerification;
    private String hostKey;
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String privateKey;
    private String passphrase;
    private String knownHosts;
    private int connectTimeout = 30000;
}
