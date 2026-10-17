package com.ifelio.ijob.plugin.filetransfer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FileTransferTaskConfigValidatorTest {
    private final FileTransferTaskConfigValidator validator = new FileTransferTaskConfigValidator();

    @Test
    void validTransferAndReadOnlyConfigurationsShouldPass() {
        FileTransferConfig transfer = base();
        transfer.setSourcePath("/in/${date}");
        transfer.setTargetPath("backup/${date}");
        transfer.setFilePattern("*.csv");
        transfer.setCompressType(CompressType.ZIP);
        transfer.setArchiveName("backup_${date}");

        FileTransferConfig readOnly = base();
        readOnly.setMode(TransferMode.READ_ONLY);
        readOnly.setTargetResourceId(null);

        assertDoesNotThrow(() -> validator.validate(transfer));
        assertDoesNotThrow(() -> validator.validate(readOnly));
    }

    @Test
    void missingResourceAndWriteOnlyModeShouldFail() {
        FileTransferConfig missing = base();
        missing.setSourceResourceId(null);
        FileTransferConfig writeOnly = base();
        writeOnly.setMode(TransferMode.WRITE_ONLY);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(missing));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(writeOnly));
    }

    @Test
    void invalidGlobAndPathPatternShouldFail() {
        FileTransferConfig malformed = base();
        malformed.setFilePattern("[");
        FileTransferConfig pathPattern = base();
        pathPattern.setFilePattern("sub/*.csv");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(malformed));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(pathPattern));
    }

    @Test
    void archiveNameTraversalAndControlCharactersShouldFail() {
        FileTransferConfig traversal = base();
        traversal.setArchiveName("../backup");
        FileTransferConfig control = base();
        control.setArchiveName("backup\r\nname");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(traversal));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(control));
    }

    @Test
    void deleteSourceShouldRequireTransferAndDifferentResources() {
        FileTransferConfig readOnly = base();
        readOnly.setMode(TransferMode.READ_ONLY);
        readOnly.setTargetResourceId(null);
        readOnly.setDeleteSource(true);
        FileTransferConfig sameResource = base();
        sameResource.setTargetResourceId(1L);
        sameResource.setDeleteSource(true);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(readOnly));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(sameResource));
    }

    @Test
    void missingArchiveEnumsShouldFail() {
        FileTransferConfig config = base();
        config.setCompressionLevel(null);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void unusedArchiveNameAndCompressionLevelShouldFail() {
        FileTransferConfig name = base();
        name.setArchiveName("unused");
        FileTransferConfig level = base();
        level.setCompressType(CompressType.TAR);
        level.setCompressionLevel(CompressionLevel.BEST);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(name));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(level));
    }

    @Test
    void invalidExtractionLimitsShouldFail() {
        FileTransferConfig count = base();
        count.setMaxArchiveEntries(0);
        FileTransferConfig entry = base();
        entry.setMaxExtractedEntryBytes(1023);
        FileTransferConfig total = base();
        total.setMaxExtractedEntryBytes(4096);
        total.setMaxExtractedTotalBytes(2048);
        FileTransferConfig ratio = base();
        ratio.setMaxCompressionRatio(1001);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(count));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(entry));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(total));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(ratio));
    }

    private FileTransferConfig base() {
        return FileTransferConfig.builder().sourceResourceId(1L).targetResourceId(2L).build();
    }
}
