package com.ifelio.ijob.plugin.localjar;

import java.nio.file.Path;

record LocalJarArtifact(
        Path path,
        String fileName,
        long size,
        String sha256,
        String implementationVersion,
        String spiVersion,
        String entryClass) {}
