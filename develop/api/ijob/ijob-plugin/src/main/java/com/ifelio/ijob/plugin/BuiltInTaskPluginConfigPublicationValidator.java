package com.ifelio.ijob.plugin;

import com.ifelio.ijob.contract.enums.PluginType;
import com.ifelio.ijob.contract.monitor.application.ProcessMatchType;
import com.ifelio.ijob.core.task.definition.TaskPluginConfigPublicationContext;
import com.ifelio.ijob.core.task.definition.TaskPluginConfigPublicationValidator;
import com.ifelio.ijob.plugin.apicall.ApiCallConfig;
import com.ifelio.ijob.plugin.apicall.ApiCallTaskConfigValidator;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfig;
import com.ifelio.ijob.plugin.batchetl.config.BatchEtlTaskConfigValidator;
import com.ifelio.ijob.plugin.common.config.PluginConfigCodec;
import com.ifelio.ijob.plugin.datacheck.DataCheckConfigValidator;
import com.ifelio.ijob.plugin.datacheck.config.CheckType;
import com.ifelio.ijob.plugin.datacheck.config.DataCheckConfig;
import com.ifelio.ijob.plugin.datacheck.config.DataCheckItemConfig;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifelio.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifelio.ijob.plugin.event.EventTaskConfig;
import com.ifelio.ijob.plugin.event.EventTaskConfigValidator;
import com.ifelio.ijob.plugin.filetransfer.FileTransferConfig;
import com.ifelio.ijob.plugin.filetransfer.FileTransferTaskConfigValidator;
import com.ifelio.ijob.plugin.localjar.LocalJarTaskConfig;
import com.ifelio.ijob.plugin.push.PushTaskConfig;
import com.ifelio.ijob.plugin.push.PushTaskConfigValidator;
import com.ifelio.ijob.plugin.script.ScriptTaskConfig;
import com.ifelio.ijob.plugin.script.ScriptTaskConfigValidator;
import com.ifelio.ijob.plugin.servermonitor.ServerMonitorTaskConfigValidator;
import com.ifelio.ijob.plugin.servermonitor.config.MonitorConfig;
import com.ifelio.ijob.plugin.servermonitor.rule.DiskMonitorRule;
import com.ifelio.ijob.plugin.servermonitor.rule.Threshold;
import com.ifelio.ijob.plugin.sql.SqlTaskConfig;
import com.ifelio.ijob.plugin.sql.SqlTaskConfigValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class BuiltInTaskPluginConfigPublicationValidator
        implements TaskPluginConfigPublicationValidator {
    private static final Set<PluginType> TYPES = Set.copyOf(EnumSet.allOf(PluginType.class));

    private final com.ifelio.ijob.core.auth.AccountService accounts;
    private final PluginConfigCodec codec;
    private final ScriptTaskConfigValidator scriptValidator;
    private final SqlTaskConfigValidator sqlValidator;
    private final ApiCallTaskConfigValidator apiCallValidator;
    private final FileTransferTaskConfigValidator fileTransferValidator;
    private final ServerMonitorTaskConfigValidator monitorValidator;
    private final PushTaskConfigValidator pushValidator;
    private final DataCheckConfigValidator dataCheckValidator;
    private final EtlTaskConfigValidator etlValidator;
    private final BatchEtlTaskConfigValidator batchEtlValidator;

    @Override
    public Set<PluginType> supportTypes() {
        return TYPES;
    }

    @Override
    public void validate(
            PluginType type, String pluginConfig, TaskPluginConfigPublicationContext context) {
        switch (type) {
            case EVENT -> validateEvent(pluginConfig, context);
            case SCRIPT ->
                    scriptValidator.validateForPublication(
                            codec.read(pluginConfig, ScriptTaskConfig.class, "Script"));
            case SQL -> sqlValidator.validate(codec.read(pluginConfig, SqlTaskConfig.class, "SQL"));
            case API_CALL ->
                    apiCallValidator.validate(
                            codec.read(pluginConfig, ApiCallConfig.class, "API call"));
            case FILE_TRANSFER ->
                    fileTransferValidator.validate(
                            codec.read(pluginConfig, FileTransferConfig.class, "File transfer"));
            case SERVER_MONITOR -> validateMonitor(pluginConfig);
            case PUSH -> validatePush(pluginConfig);
            case DATA_CHECK -> validateDataCheck(pluginConfig);
            case ETL -> validateEtl(pluginConfig, context.retryTimes());
            case BATCH_ETL -> validateBatchEtl(pluginConfig, context.retryTimes());
            case LOCAL_JAR -> codec.read(pluginConfig, LocalJarTaskConfig.class, "Local JAR");
        }
    }

    @Override
    public Set<Long> referencedResourceIds(PluginType type, String pluginConfig) {
        Set<Long> ids = new LinkedHashSet<>();
        switch (type) {
            case EVENT ->
                    add(
                            ids,
                            codec.read(pluginConfig, EventTaskConfig.class, "Event")
                                    .getFileResourceId());
            case SCRIPT ->
                    add(
                            ids,
                            codec.read(pluginConfig, ScriptTaskConfig.class, "Script")
                                    .getSshResourceId());
            case SQL ->
                    add(ids, codec.read(pluginConfig, SqlTaskConfig.class, "SQL").getResourceId());
            case API_CALL ->
                    add(
                            ids,
                            codec.read(pluginConfig, ApiCallConfig.class, "API call")
                                    .getResourceId());
            case FILE_TRANSFER ->
                    addFileTransferResources(
                            ids,
                            codec.read(pluginConfig, FileTransferConfig.class, "File transfer"));
            case SERVER_MONITOR ->
                    add(
                            ids,
                            codec.read(pluginConfig, MonitorConfig.class, "Server monitor")
                                    .getResourceId());
            case PUSH ->
                    addPushResources(ids, codec.read(pluginConfig, PushTaskConfig.class, "Push"));
            case DATA_CHECK ->
                    add(
                            ids,
                            codec.read(pluginConfig, DataCheckConfig.class, "Data check")
                                    .getResourceId());
            case ETL -> addEtlResources(ids, codec.read(pluginConfig, EtlTaskConfig.class, "ETL"));
            case BATCH_ETL ->
                    addBatchEtlResources(
                            ids, codec.read(pluginConfig, BatchEtlTaskConfig.class, "Batch ETL"));
            case LOCAL_JAR -> {
                // Local JAR artifacts are snapshotted files, not resources.
            }
        }
        return Set.copyOf(ids);
    }

    private void addFileTransferResources(Set<Long> ids, FileTransferConfig config) {
        add(ids, config.getSourceResourceId());
        add(ids, config.getTargetResourceId());
    }

    private void addPushResources(Set<Long> ids, PushTaskConfig config) {
        add(ids, config.getSourceResourceId());
        if (config.getTarget() != null) {
            add(ids, config.getTarget().getResourceId());
        }
    }

    private void addEtlResources(Set<Long> ids, EtlTaskConfig config) {
        if (config.getSource() != null) {
            add(ids, config.getSource().getResourceId());
        }
        if (config.getTarget() != null) {
            add(ids, config.getTarget().getResourceId());
        }
        if (config.getTransforms() != null) {
            config.getTransforms().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(
                            item ->
                                    item.getProperties() == null
                                            ? null
                                            : item.getProperties().get("resourceId"))
                    .forEach(value -> addNumber(ids, value));
        }
        if (config.getValidations() != null) {
            config.getValidations().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(
                            item ->
                                    item.getProperties() == null
                                            ? null
                                            : item.getProperties().get("resourceId"))
                    .forEach(value -> addNumber(ids, value));
        }
    }

    private void addBatchEtlResources(Set<Long> ids, BatchEtlTaskConfig config) {
        add(ids, config.getSourceResourceId());
        add(ids, config.getTargetResourceId());
    }

    private void addNumber(Set<Long> ids, Object value) {
        if (value instanceof Number number) {
            add(ids, number.longValue());
        }
    }

    private void add(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

    private void validateEvent(String pluginConfig, TaskPluginConfigPublicationContext context) {
        EventTaskConfig config = codec.read(pluginConfig, EventTaskConfig.class, "Event");
        EventTaskConfigValidator.validate(config, context.timeout());
        if (config.getAction() == com.ifelio.ijob.contract.enums.EventAction.WAIT_APPROVAL)
            accounts.validateApprovers(config.getAllowedOperatorIds());
        if ((config.getAction() == null
                        || config.getAction() == com.ifelio.ijob.contract.enums.EventAction.PUBLISH)
                && config.getPayload() != null) {
            String payload = codec.write(config.getPayload(), "Event payload");
            if (!payload.contains("${")
                    && payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384)
                throw new IllegalArgumentException("Event payload exceeds 16384 bytes");
        }
    }

    private void validateMonitor(String pluginConfig) {
        MonitorConfig config = codec.read(pluginConfig, MonitorConfig.class, "Server monitor");
        sanitizeMonitorTemplates(config);
        monitorValidator.validate(config);
    }

    private void validatePush(String pluginConfig) {
        PushTaskConfig config = codec.read(pluginConfig, PushTaskConfig.class, "Push");
        if (config.getSql() != null && config.getSql().contains("${")) {
            config.setSql("SELECT 1");
        }
        if (config.getTarget() != null && config.getTarget().getProperties() != null) {
            Map<String, Object> properties = config.getTarget().getProperties();
            sanitizeTargetProperty(properties, "exchange", "runtime");
            sanitizeTargetProperty(properties, "routingKey", "runtime");
            sanitizeTargetProperty(properties, "topic", "runtime-topic");
        }
        pushValidator.validate(config);
    }

    private void validateDataCheck(String pluginConfig) {
        DataCheckConfig config = codec.read(pluginConfig, DataCheckConfig.class, "Data check");
        if (config.getChecks() != null) {
            for (DataCheckItemConfig item : config.getChecks()) {
                if (item != null && item.getSql() != null && item.getSql().contains("${")) {
                    item.setSql("SELECT 1");
                }
                if (item != null) {
                    item.setExpected(sanitizeCheckValue(item.getExpected(), item.getType()));
                    item.setMinValue(sanitizeCheckValue(item.getMinValue(), item.getType()));
                    item.setMaxValue(sanitizeCheckValue(item.getMaxValue(), item.getType()));
                }
            }
        }
        dataCheckValidator.validate(config);
        if (config.getHandlers().stream().anyMatch(h -> "VARIABLE".equalsIgnoreCase(h.getType()))) {
            java.util.Set<String> outputs =
                    new java.util.HashSet<>(
                            java.util.Set.of(
                                    "data_check_success",
                                    "data_check_total",
                                    "data_check_failed",
                                    "data_check_result"));
            for (DataCheckItemConfig item : config.getChecks()) {
                if (!outputs.add(item.getName()))
                    throw new IllegalArgumentException("Duplicate check output key");
            }
            for (DataCheckItemConfig item : config.getChecks()) {
                if (item.getVariable() != null && !outputs.add(item.getVariable()))
                    throw new IllegalArgumentException("Duplicate check output key");
            }
        }
    }

    private void sanitizeMonitorTemplates(MonitorConfig config) {
        Map<String, String> applicationNames = new LinkedHashMap<>();
        List<String> orderedApplicationNames = new ArrayList<>();
        if (config.getCollect() != null && config.getCollect().getApplications() != null) {
            int index = 0;
            for (var application : config.getCollect().getApplications()) {
                if (application == null) {
                    continue;
                }
                String name = application.getName();
                if (containsTemplate(name)) {
                    String replacement =
                            applicationNames.computeIfAbsent(
                                    name, ignored -> "runtime-app-" + applicationNames.size());
                    application.setName(replacement);
                }
                orderedApplicationNames.add(application.getName());
                if (containsTemplate(application.getMatchValue())) {
                    application.setMatchValue(
                            application.getMatchType() == ProcessMatchType.PID
                                    ? "1"
                                    : "runtime-match-" + index);
                }
                index++;
            }
        }
        if (config.getRules() == null) {
            return;
        }
        if (config.getRules().getApplications() != null) {
            int index = 0;
            for (var rule : config.getRules().getApplications()) {
                if (rule != null && containsTemplate(rule.getName())) {
                    rule.setName(
                            applicationNames.getOrDefault(
                                    rule.getName(),
                                    index < orderedApplicationNames.size()
                                            ? orderedApplicationNames.get(index)
                                            : "runtime-rule-" + index));
                }
                index++;
            }
        }
        sanitizeDiskPaths(config.getRules().getDisk());
    }

    private void sanitizeDiskPaths(DiskMonitorRule rule) {
        if (rule == null || rule.getPaths() == null) {
            return;
        }
        Map<String, Threshold> paths = new LinkedHashMap<>();
        int index = 0;
        for (var entry : rule.getPaths().entrySet()) {
            String path =
                    containsTemplate(entry.getKey()) ? "runtime-path-" + index : entry.getKey();
            paths.put(path, entry.getValue());
            index++;
        }
        rule.setPaths(paths);
    }

    private void sanitizeTargetProperty(
            Map<String, Object> properties, String name, String replacement) {
        Object value = properties.get(name);
        if (containsTemplate(value)) {
            properties.put(name, replacement);
        }
    }

    private Object sanitizeCheckValue(Object value, CheckType type) {
        if (!containsTemplate(value)) {
            return value;
        }
        if (type == CheckType.COUNT) {
            return 0;
        }
        if (type == CheckType.EXISTS) {
            return false;
        }
        return "runtime";
    }

    private boolean containsTemplate(Object value) {
        return value instanceof String text && text.contains("${");
    }

    private void validateEtl(String pluginConfig, int retryTimes) {
        EtlTaskConfig config = codec.read(pluginConfig, EtlTaskConfig.class, "ETL");
        if (pluginConfig.contains("${")) {
            etlValidator.validateUnresolved(config);
        } else {
            etlValidator.validate(config, retryTimes);
        }
    }

    private void validateBatchEtl(String pluginConfig, int retryTimes) {
        BatchEtlTaskConfig config = codec.read(pluginConfig, BatchEtlTaskConfig.class, "Batch ETL");
        // Substitute only fields that can actually contain runtime expressions. Never let
        // a template in one value disable validation of limits, modes or retry safeguards.
        config.setSourceSchema(batchIdentifier(config.getSourceSchema(), "runtime_source"));
        config.setTargetSchema(batchIdentifier(config.getTargetSchema(), "runtime_target"));
        if (config.getTables() != null) {
            List<String> tables = new ArrayList<>();
            for (int i = 0; i < config.getTables().size(); i++)
                tables.add(batchIdentifier(config.getTables().get(i), "runtime_table_" + i));
            config.setTables(tables);
        }
        if (config.getLogicalKeys() != null) {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < config.getLogicalKeys().size(); i++)
                keys.add(batchIdentifier(config.getLogicalKeys().get(i), "runtime_key_" + i));
            config.setLogicalKeys(keys);
        }
        if (config.getTargetNaming() != null) {
            config.getTargetNaming()
                    .setPrefix(batchIdentifier(config.getTargetNaming().getPrefix(), "runtime_"));
            config.getTargetNaming()
                    .setSuffix(batchIdentifier(config.getTargetNaming().getSuffix(), "_runtime"));
        }
        if (config.getIncremental() != null)
            config.getIncremental()
                    .setWatermarkColumn(
                            batchIdentifier(
                                    config.getIncremental().getWatermarkColumn(), "runtime_mark"));
        batchEtlValidator.validate(config, retryTimes);
    }

    private String batchIdentifier(String value, String replacement) {
        return containsTemplate(value) ? replacement : value;
    }
}
