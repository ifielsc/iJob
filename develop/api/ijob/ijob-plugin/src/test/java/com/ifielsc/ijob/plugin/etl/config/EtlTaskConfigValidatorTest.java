package com.ifielsc.ijob.plugin.etl.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.plugin.etl.transformer.TransformConfig;
import com.ifielsc.ijob.plugin.etl.transformer.TransformType;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class EtlTaskConfigValidatorTest {
    private final EtlTaskConfigValidator validator = new EtlTaskConfigValidator();

    @Test
    void shouldBindAndValidateMultiSheetJsonWithoutTopLevelSelectorDefaults() throws Exception {
        EtlTaskConfig config = validConfig();
        config.setSource(
                new ObjectMapper()
                        .readValue(
                                """
{
  "type": "FILE", "resourceId": 1, "path": "source.xlsx",
  "format": "EXCEL", "readMode": "SQL",
  "sheets": [
    {"sheetName": "行情", "tableName": "PRICES", "headerRow": 2, "startRow": 4},
    {"sheetIndex": 1, "tableName": "SECURITIES", "stagingColumns": ["代码", "名称"]}
  ],
  "sql": "SELECT p.* FROM PRICES p JOIN SECURITIES s ON p.id = s.id"
}
""",
                                EtlTaskConfig.SourceConfig.class));
        assertDoesNotThrow(() -> validator.validate(config));
        assertEquals(2, config.getSource().getSheets().size());
        assertEquals(1, config.getSource().getSheets().getLast().getSheetIndex());
    }

    @Test
    void shouldRejectInvalidMultiSheetSourceConfigurations() {
        List<Consumer<EtlTaskConfig>> mutations =
                List.of(
                        config -> config.getSource().setType(EtlTaskConfig.SourceType.DATABASE),
                        config -> config.getSource().setFormat(EtlTaskConfig.FileFormat.CSV),
                        config -> config.getSource().setReadMode(EtlTaskConfig.FileReadMode.DIRECT),
                        config -> config.getSource().setReadMode(null),
                        config -> config.getSource().setSheetName("legacy"),
                        config -> config.getSource().setSheetIndex(0),
                        config -> config.getSource().setHeader(false),
                        config -> config.getSource().setHeaderRow(1),
                        config -> config.getSource().setStartRow(1),
                        config -> config.getSource().setStagingTable("IGNORED"),
                        config -> config.getSource().setStagingColumns(List.of("ignored")),
                        config -> config.getSource().setSheets(null),
                        config -> config.getSource().getSheets().add(null),
                        config -> config.getExecution().setMaxExcelSheets(1));
        for (int index = 0; index < mutations.size(); index++) {
            EtlTaskConfig config = multiSheetConfig();
            mutations.get(index).accept(config);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.validate(config),
                    "mutation " + index);
        }
    }

    @Test
    void shouldRejectInvalidSheetMappings() {
        List<Consumer<EtlTaskConfig.ExcelSheetConfig>> mutations =
                List.of(
                        sheet -> sheet.setSheetName(null),
                        sheet -> sheet.setSheetName(" "),
                        sheet -> sheet.setSheetIndex(0),
                        sheet -> {
                            sheet.setSheetName(null);
                            sheet.setSheetIndex(-1);
                        },
                        sheet -> sheet.setSheetName("b"),
                        sheet -> sheet.setTableName("b"),
                        sheet -> sheet.setTableName("bad; DROP TABLE x"),
                        sheet -> sheet.setTableName(null),
                        sheet -> sheet.setHeaderRow(-1),
                        sheet -> sheet.setHeaderRow(null),
                        sheet -> sheet.setStartRow(-1),
                        sheet -> sheet.setStartRow(0),
                        sheet -> sheet.setStagingColumns(null),
                        sheet -> sheet.setStagingColumns(List.of("id", "id")),
                        sheet -> sheet.setStagingColumns(List.of(" ")));
        for (int index = 0; index < mutations.size(); index++) {
            EtlTaskConfig config = multiSheetConfig();
            mutations.get(index).accept(config.getSource().getSheets().getFirst());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.validate(config),
                    "mutation " + index);
        }
    }

    private EtlTaskConfig multiSheetConfig() {
        EtlTaskConfig config = validConfig();
        config.getSource().setType(EtlTaskConfig.SourceType.FILE);
        config.getSource().setPath("source.xlsx");
        config.getSource().setFormat(EtlTaskConfig.FileFormat.EXCEL);
        config.getSource().setReadMode(EtlTaskConfig.FileReadMode.SQL);
        List<EtlTaskConfig.ExcelSheetConfig> sheets = new ArrayList<>();
        for (String name : List.of("A", "B")) {
            EtlTaskConfig.ExcelSheetConfig sheet = new EtlTaskConfig.ExcelSheetConfig();
            sheet.setSheetName(name);
            sheet.setTableName(name);
            sheets.add(sheet);
        }
        config.getSource().setSheets(sheets);
        return config;
    }

    @Test
    void shouldAcceptDatabaseUpsertConfig() {
        assertDoesNotThrow(() -> validator.validate(validConfig()));
    }

    @Test
    void shouldRejectLogicalKeyThatIsNotMapped() {
        EtlTaskConfig config = validConfig();
        config.getTarget().getLogicalKeys().add("missing_key");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectIncrementalAppendMode() {
        EtlTaskConfig config = validConfig();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.APPEND);
        config.getIncremental().setEnabled(true);
        config.getIncremental().setCheckpointKey("orders");
        config.getIncremental().setWatermarkField("updated_at");
        config.getIncremental().setInitialWatermark("2024-01-01T00:00:00");
        config.getIncremental().setUpperBoundSql("SELECT CURRENT_TIMESTAMP");
        config.getSource()
                .setSql(
                        "SELECT * FROM source "
                                + "WHERE updated_at > :etlLastWatermark "
                                + "AND updated_at <= :etlUpperWatermark");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRequireAcknowledgementForAppendRetries() {
        EtlTaskConfig config = validConfig();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.APPEND);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config, 1));

        config.setAllowNonIdempotentRetry(true);
        assertDoesNotThrow(() -> validator.validate(config, 1));
    }

    @Test
    void shouldRequireInitialValuesForIncrementalTieBreakers() {
        EtlTaskConfig config = validConfig();
        config.getIncremental().setEnabled(true);
        config.getIncremental().setCheckpointKey("orders");
        config.getIncremental().setWatermarkField("updated_at");
        config.getIncremental().setInitialWatermark("2024-01-01T00:00:00");
        config.getIncremental().setUpperBoundSql("SELECT CURRENT_TIMESTAMP");
        config.getIncremental().getTieBreakerFields().add("id");
        config.getSource()
                .setSql(
                        "SELECT * FROM source "
                                + "WHERE updated_at > :etlLastWatermark "
                                + "AND updated_at <= :etlUpperWatermark "
                                + "AND id > :etlLastTie1");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));

        config.getIncremental().getInitialTieBreakerValues().add(0L);
        assertDoesNotThrow(() -> validator.validate(config));
    }

    @Test
    void shouldRejectMultiCharacterSourceDelimiter() {
        EtlTaskConfig config = validConfig();
        config.getSource().setType(EtlTaskConfig.SourceType.FILE);
        config.getSource().setPath("orders.csv");
        config.getSource().setFormat(EtlTaskConfig.FileFormat.CSV);
        config.getSource().setDelimiter("||");

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectMappedScd2GeneratedColumn() {
        EtlTaskConfig config = validConfig();
        config.getTarget().setMode(EtlTaskConfig.LoadMode.SCD2);
        config.getTarget().getColumns().put("valid_from", "valid_from");
        EtlTaskConfig.SCD2Config scd2 = new EtlTaskConfig.SCD2Config();
        scd2.getBusinessKeys().add("id");
        scd2.getCompareFields().add("name");
        scd2.setEffectiveValueField("updated_at");
        scd2.setValidFromColumn("valid_from");
        scd2.setValidToColumn("valid_to");
        config.getTarget().setScd2(scd2);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectLookupWithoutBoundedKeysPlaceholder() {
        EtlTaskConfig config = validConfig();
        TransformConfig lookup = new TransformConfig();
        lookup.setType(TransformType.LOOKUP);
        lookup.getProperties().put("sourceField", "code");
        lookup.getProperties().put("targetField", "name");
        lookup.getProperties().put("resourceId", 3L);
        lookup.getProperties().put("sql", "SELECT code, name FROM ref");
        lookup.getProperties().put("keyColumn", "code");
        lookup.getProperties().put("valueColumn", "name");
        config.getTransforms().add(lookup);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectUnsupportedStreamingLastWinsDeduplication() {
        EtlTaskConfig config = validConfig();
        TransformConfig deduplicate = new TransformConfig();
        deduplicate.setType(TransformType.DEDUPLICATE);
        deduplicate.getProperties().put("fields", java.util.List.of("id"));
        deduplicate.getProperties().put("keep", "LAST");
        config.getTransforms().add(deduplicate);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    @Test
    void shouldRejectDeduplicationForIncrementalExtraction() {
        EtlTaskConfig config = validConfig();
        config.getIncremental().setEnabled(true);
        config.getIncremental().setCheckpointKey("orders");
        config.getIncremental().setWatermarkField("updated_at");
        config.getIncremental().setInitialWatermark("2024-01-01T00:00:00");
        config.getIncremental().setUpperBoundSql("SELECT CURRENT_TIMESTAMP");
        config.getSource()
                .setSql(
                        "SELECT * FROM source "
                                + "WHERE updated_at > :etlLastWatermark "
                                + "AND updated_at <= :etlUpperWatermark");
        TransformConfig deduplicate = new TransformConfig();
        deduplicate.setType(TransformType.DEDUPLICATE);
        deduplicate.getProperties().put("fields", java.util.List.of("id"));
        config.getTransforms().add(deduplicate);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(config));
    }

    private EtlTaskConfig validConfig() {
        EtlTaskConfig config = new EtlTaskConfig();
        EtlTaskConfig.SourceConfig source = new EtlTaskConfig.SourceConfig();
        source.setType(EtlTaskConfig.SourceType.DATABASE);
        source.setResourceId(1L);
        source.setSql("SELECT id, name FROM source");
        config.setSource(source);

        EtlTaskConfig.TargetConfig target = new EtlTaskConfig.TargetConfig();
        target.setType(EtlTaskConfig.TargetType.DATABASE);
        target.setResourceId(2L);
        target.setTable("target_table");
        target.getColumns().put("id", "id");
        target.getColumns().put("name", "name");
        target.getLogicalKeys().add("id");
        target.setMode(EtlTaskConfig.LoadMode.UPSERT);
        config.setTarget(target);
        return config;
    }
}
