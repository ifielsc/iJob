package com.ifielsc.ijob.plugin.filetransfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferFile {
    /** Full source path. */
    private String sourcePath;

    private String relativePath;

    private String fileName;

    /** Target-relative path. */
    private String targetPath;

    /** Local staging file. */
    private File localFile;

    private long size;

    private boolean directory;

    private LocalDateTime lastModified;
}
