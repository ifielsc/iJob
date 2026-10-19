package com.ifielsc.ijob.plugin.localjar;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocalJarTaskConfig {
    public static final int FORMAT_VERSION = 1;

    private Integer formatVersion;
    private String jarFile;
    private Long jarSize;
    private String sha256;
    private String implementationVersion;
    private String spiVersion;
    private String entryClass;
    private JsonNode userConfig;
}
