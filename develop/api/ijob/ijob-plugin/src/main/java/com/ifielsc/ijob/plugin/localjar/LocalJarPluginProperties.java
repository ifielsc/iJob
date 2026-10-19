package com.ifielsc.ijob.plugin.localjar;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Data
@ConfigurationProperties(prefix = "app.plugin.local-jar")
public class LocalJarPluginProperties {
    private boolean enabled = false;
    private Path rootDirectory;
    private long maxJarBytes = 64L * 1024 * 1024;
    private int maxJarEntries = 10_000;
    private long maxExpandedBytes = 256L * 1024 * 1024;
    private int maxCompressionRatio = 100;
    private int maxCachedLoaders = 32;
}
