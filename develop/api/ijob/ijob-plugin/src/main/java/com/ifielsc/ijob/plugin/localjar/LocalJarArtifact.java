// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.localjar;

import java.nio.file.Path;

record LocalJarArtifact(
        Path path,
        String fileName,
        long size,
        String sha256,
        String implementationVersion,
        String spiVersion,
        String entryClass) {}
