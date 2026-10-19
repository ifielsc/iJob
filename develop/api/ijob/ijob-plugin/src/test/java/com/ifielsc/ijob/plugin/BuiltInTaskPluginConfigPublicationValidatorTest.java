package com.ifielsc.ijob.plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifielsc.ijob.contract.enums.PluginType;
import com.ifielsc.ijob.core.task.definition.TaskPluginConfigPublicationContext;
import com.ifielsc.ijob.plugin.apicall.ApiCallTaskConfigValidator;
import com.ifielsc.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.common.config.PluginConfigCodec;
import com.ifielsc.ijob.plugin.datacheck.DataCheckConfigValidator;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.filetransfer.FileTransferTaskConfigValidator;
import com.ifielsc.ijob.plugin.push.PushTaskConfigValidator;
import com.ifielsc.ijob.plugin.script.ScriptTaskConfigValidator;
import com.ifielsc.ijob.plugin.servermonitor.ServerMonitorTaskConfigValidator;
import com.ifielsc.ijob.plugin.sql.SqlTaskConfigValidator;

import org.junit.jupiter.api.Test;

class BuiltInTaskPluginConfigPublicationValidatorTest {
    @Test
    void monitorRejectsFractionalReferencesAndTrailingJson() {
        String config =
                "{\"resourceId\":1,\"collect\":{\"items\":[\"CPU\"]},\"rules\":{\"cpu\":{\"usage\":{\"warning\":80}}}}";
        assertDoesNotThrow(() -> validator.validate(PluginType.SERVER_MONITOR, config, context()));
        String notified =
                config.substring(0, config.length() - 1)
                        + ",\"notify\":{\"enabled\":true,\"notifyConfigId\":\"9223372036854775807\"}}";
        assertDoesNotThrow(
                () -> validator.validate(PluginType.SERVER_MONITOR, notified, context()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validator.validate(
                                PluginType.SERVER_MONITOR,
                                notified.replace("\"9223372036854775807\"", "1.5"),
                                context()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validator.validate(
                                PluginType.SERVER_MONITOR,
                                config.replace("resourceId\":1", "resourceId\":1.5"),
                                context()));
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(PluginType.SERVER_MONITOR, config + " {}", context()));
        var codec = new PluginConfigCodec(new ObjectMapper());
        var parsed =
                codec.read(
                        config.replace("resourceId\":1", "resourceId\":9223372036854775807"),
                        com.ifielsc.ijob.plugin.servermonitor.config.MonitorConfig.class,
                        "Monitor");
        org.junit.jupiter.api.Assertions.assertEquals(Long.MAX_VALUE, parsed.getResourceId());
    }

    @Test
    void batchTemplatesMustNotBypassStaticLimitsAndRetryRules() {
        String config =
                """
                {"sourceResourceId":1,"targetResourceId":2,"tables":["${table!}"],
                 "loadMode":"APPEND","execution":{"writeBatchSize":0}}
                """;
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(PluginType.BATCH_ETL, config, context()));
        String valid = config.replace("\"writeBatchSize\":0", "\"writeBatchSize\":100");
        assertDoesNotThrow(() -> validator.validate(PluginType.BATCH_ETL, valid, context()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validator.validate(
                                PluginType.BATCH_ETL,
                                valid,
                                new TaskPluginConfigPublicationContext(1, null)));
    }

    private final BuiltInTaskPluginConfigPublicationValidator validator =
            new BuiltInTaskPluginConfigPublicationValidator(
                    null,
                    new PluginConfigCodec(new ObjectMapper()),
                    new ScriptTaskConfigValidator(),
                    new SqlTaskConfigValidator(),
                    new ApiCallTaskConfigValidator(),
                    new FileTransferTaskConfigValidator(),
                    new ServerMonitorTaskConfigValidator(),
                    new PushTaskConfigValidator(),
                    new DataCheckConfigValidator(),
                    new EtlTaskConfigValidator(),
                    new BatchEtlTaskConfigValidator());

    @Test
    void pushRuntimeTemplatesShouldNotBypassOtherStaticValidation() {
        String config =
                """
                {
                  "sourceResourceId": 1,
                  "sql": "select id from source where id=${id!}",
                  "target": {
                    "resourceId": 2,
                    "type": "KAFKA",
                    "properties": {"topic": "${topic!}"}
                  }
                }
                """;

        assertDoesNotThrow(() -> validator.validate(PluginType.PUSH, config, context()));
    }

    @Test
    void dataCheckRuntimeValuesShouldAllowStaticValidation() {
        String config =
                """
                {
                  "resourceId": 1,
                  "checks": [{
                    "name": "count-check",
                    "sql": "select count(*) from t where id=${id!}",
                    "type": "COUNT",
                    "compareType": "EQ",
                    "expected": "${expected!}"
                  }],
                  "handlers": []
                }
                """;

        assertDoesNotThrow(() -> validator.validate(PluginType.DATA_CHECK, config, context()));
    }

    @Test
    void eventWaitShouldUseItsOwnDurableTimeout() {
        String config =
                """
                {
                  "action": "WAIT_EVENT",
                  "eventCode": "data.ready",
                  "timeoutSeconds": 600
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(PluginType.EVENT, config, context()));
        assertDoesNotThrow(
                () ->
                        validator.validate(
                                PluginType.EVENT,
                                config,
                                new TaskPluginConfigPublicationContext(0, null)));
    }

    @Test
    void lightweightFileProbeShouldPassStaticValidation() {
        String config =
                """
                {
                  "action": "PROBE_FILE",
                  "eventCode": "file.ready",
                  "fileResourceId": 7,
                  "filePath": "/in",
                  "filePattern": "*.csv"
                }
                """;

        assertDoesNotThrow(() -> validator.validate(PluginType.EVENT, config, context()));
    }

    private TaskPluginConfigPublicationContext context() {
        return new TaskPluginConfigPublicationContext(0, 60);
    }
}
