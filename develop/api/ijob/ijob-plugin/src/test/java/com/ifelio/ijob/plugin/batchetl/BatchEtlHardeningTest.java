package com.ifelio.ijob.plugin.batchetl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifelio.ijob.plugin.batchetl.metadata.JdbcMetadataInspector;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

class BatchEtlHardeningTest {
    private final PluginConfigCodec codec = new PluginConfigCodec(new ObjectMapper());

    @Test
    void preservesExactDecimalsThroughResolution() {
        String json =
                "{\"incremental\":{\"initialWatermark\":9007199254740993.123456789,\"initialLogicalKeyValues\":[9007199254740993.123456789]}}";
        var config = codec.read(json, BatchEtlTaskConfig.class, "Batch ETL");
        var resolved =
                codec.convert(
                        codec.toMap(config, "Batch ETL"), BatchEtlTaskConfig.class, "Batch ETL");
        assertEquals(
                new BigDecimal("9007199254740993.123456789"),
                resolved.getIncremental().getInitialWatermark());
        assertEquals(
                new BigDecimal("9007199254740993.123456789"),
                resolved.getIncremental().getInitialLogicalKeyValues().getFirst());
    }

    @Test
    void rejectsFractionalIntegerAndTrailingDocument() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        codec.read(
                                "{\"sourceResourceId\":1.5}",
                                BatchEtlTaskConfig.class,
                                "Batch ETL"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.read("{} {}", BatchEtlTaskConfig.class, "Batch ETL"));
    }

    @Test
    void underscoresIdentifyExactTableEvenWhenSimilarTableExists() throws Exception {
        try (var source = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
                var target = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            source.createStatement().execute("CREATE TABLE ORDER_ITEM (ID BIGINT)");
            source.createStatement().execute("CREATE TABLE ORDERXITEM (WRONG_COL BIGINT)");
            target.createStatement().execute("CREATE TABLE ORDER_ITEM (ID BIGINT)");
            var plan =
                    new JdbcMetadataInspector()
                            .inspect(source, target, config(), "ORDER_ITEM", "ORDER_ITEM");
            assertEquals("ORDER_ITEM", plan.sourceTable());
            assertEquals(1, plan.columns().size());
        }
    }

    @Test
    void updateOnlyDoesNotRequireInsertOnlyColumns() throws Exception {
        try (var source = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
                var target = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            source.createStatement().execute("CREATE TABLE ORDERS (ID BIGINT, AMOUNT DECIMAL)");
            target.createStatement()
                    .execute(
                            "CREATE TABLE ORDERS (ID BIGINT, AMOUNT DECIMAL, REQUIRED_NAME VARCHAR"
                                    + " NOT NULL)");
            var config = config();
            config.setLoadMode(BatchEtlTaskConfig.LoadMode.UPDATE_ONLY);
            assertEquals(
                    2,
                    new JdbcMetadataInspector()
                            .inspect(source, target, config, "ORDERS", "ORDERS")
                            .columns()
                            .size());
        }
    }

    @Test
    void rejectsCaseEquivalentKeysBeforeRuntime() {
        var config = config();
        config.setLogicalKeys(List.of("id", "ID"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BatchEtlTaskConfigValidator().validate(config, 0));
    }

    private BatchEtlTaskConfig config() {
        var config = new BatchEtlTaskConfig();
        config.setSourceResourceId(1L);
        config.setTargetResourceId(2L);
        config.setTables(List.of("ORDERS"));
        config.setLogicalKeys(List.of("ID"));
        return config;
    }
}
