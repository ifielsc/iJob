package com.ifelio.ijob.core.resource.factory.file;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(
        exclude = {
            "hostKey",
            "host",
            "username",
            "password",
            "privateKey",
            "passphrase",
            "knownHosts",
            "basePath",
            "endpoint",
            "accessKey",
            "secretKey",
            "sessionToken",
            "bucket"
        })
public class FileResourceConfig {
    private FileProtocol protocol;
    private String hostKeyVerification;
    private String hostKey;
    private String host;
    private Integer port;
    private String username;
    private String password;

    /** Optional private key. */
    private String privateKey;

    /** Private-key passphrase. */
    private String passphrase;

    private String knownHosts;
    private String basePath;
    private int timeout = 30000;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private String sessionToken;
    private boolean pathStyleAccess;
}
