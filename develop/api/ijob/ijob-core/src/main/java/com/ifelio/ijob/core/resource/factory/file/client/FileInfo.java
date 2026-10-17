package com.ifelio.ijob.core.resource.factory.file.client;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FileInfo {
    private String name;
    private boolean directory;
    private long size;
    private LocalDateTime lastModified;
}
