package com.ifelio.ijob.plugin.batchetl.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;

class BatchEtlTaskConfigValidatorTest {
    private final BatchEtlTaskConfigValidator validator = new BatchEtlTaskConfigValidator();

    @Test
    void shouldAcceptFullUpsertConfig() {
        assertDoesNotThrow(() -> validator.validate(validConfig(), 2));
    }

    @Test
    void shouldRequireExplicitTruncateAcknowledgement() {
        BatchEtlTaskConfig config = validConfig();
        config.setClearBeforeImport(true);
        config.setClearMode(BatchEtlTaskConfig.ClearMode.TRUNCATE);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));

        config.setAcknowledgeNonTransactionalTruncate(true);
        assertDoesNotThrow(() -> validator.validate(config, 0));
    }

    @Test
    void shouldRequirePartialCommitAcknowledgementForDeleteClear() {
        BatchEtlTaskConfig config = validConfig();
        config.setClearBeforeImport(true);
        config.setClearMode(BatchEtlTaskConfig.ClearMode.DELETE);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));

        config.setAcknowledgePartialCommit(true);
        assertDoesNotThrow(() -> validator.validate(config, 0));
    }

    @Test
    void shouldRejectClearForIncrementalMode() {
        BatchEtlTaskConfig config = incrementalConfig();
        config.setClearBeforeImport(true);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));
    }

    @Test
    void shouldRequireInitialValueForEveryLogicalKey() {
        BatchEtlTaskConfig config = incrementalConfig();
        config.getIncremental().setInitialLogicalKeyValues(List.of(0L));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));
    }

    @Test
    void shouldGuardAppendRetries() {
        BatchEtlTaskConfig config = validConfig();
        config.setLoadMode(BatchEtlTaskConfig.LoadMode.APPEND);
        config.setLogicalKeys(List.of());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 1));

        config.setAllowNonIdempotentRetry(true);
        assertDoesNotThrow(() -> validator.validate(config, 1));
    }

    @Test
    void shouldRejectDuplicateTablesAndInvalidNaming() {
        BatchEtlTaskConfig config = validConfig();
        config.setTables(List.of("orders", "orders"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));

        config.setTables(List.of("orders"));
        config.getTargetNaming().setPrefix("bad-");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 0));
    }

    private BatchEtlTaskConfig validConfig() {
        BatchEtlTaskConfig config = new BatchEtlTaskConfig();
        config.setSourceResourceId(1L);
        config.setTargetResourceId(2L);
        config.setTables(List.of("orders", "customers"));
        config.setLogicalKeys(List.of("tenant_id", "id"));
        return config;
    }

    private BatchEtlTaskConfig incrementalConfig() {
        BatchEtlTaskConfig config = validConfig();
        config.setExtractMode(BatchEtlTaskConfig.ExtractMode.INCREMENTAL);
        config.getIncremental().setWatermarkColumn("updated_at");
        config.getIncremental().setInitialWatermark("2024-01-01T00:00:00");
        config.getIncremental().setInitialLogicalKeyValues(List.of(0L, 0L));
        return config;
    }
}
