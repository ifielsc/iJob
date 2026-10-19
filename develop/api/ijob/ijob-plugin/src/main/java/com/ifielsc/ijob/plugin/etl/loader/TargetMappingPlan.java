// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.plugin.etl.loader;

import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfig;
import com.ifielsc.ijob.plugin.etl.config.EtlTaskConfigValidator;
import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;

import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Execution-local resolved columns; never modifies the published task configuration. */
public final class TargetMappingPlan {
    public record Column(String name, boolean generated, boolean required) {}

    private final Set<String> sourceFields;
    private final List<Column> targetSchema;
    private final EtlTaskConfig.TargetConfig resolved;

    private TargetMappingPlan(
            Set<String> sourceFields,
            List<Column> targetSchema,
            EtlTaskConfig.TargetConfig resolved) {
        this.sourceFields = Set.copyOf(sourceFields);
        this.targetSchema = List.copyOf(targetSchema);
        this.resolved = resolved;
    }

    public static EtlTaskConfig.TargetConfig resolve(
            EtlContext context,
            EtlTaskConfig.TargetConfig target,
            List<Column> columns,
            List<DataRecord> records) {
        if (records.isEmpty())
            throw new IllegalArgumentException("Cannot infer mapping without records");
        TargetMappingPlan plan = context.getTargetMappingPlan();
        if (plan == null) {
            plan = create(target, columns, records.getFirst());
            plan.validateRows(records);
            context.setTargetMappingPlan(plan);
            context.getTaskContext()
                    .putVariable(
                            "etl.resolvedColumns", new LinkedHashMap<>(plan.resolved.getColumns()));
            Map<String, String> resolvedColumns = plan.resolved.getColumns();
            List<String> unmatched =
                    plan.sourceFields.stream()
                            .filter(field -> !resolvedColumns.containsValue(field))
                            .filter(field -> !target.getExcludedFields().contains(field))
                            .sorted()
                            .toList();
            context.getTaskContext().putVariable("etl.unmatchedSourceFields", unmatched);
            context.getTaskContext()
                    .putVariable(
                            "etl.excludedSourceFields", List.copyOf(target.getExcludedFields()));
            context.getTaskContext()
                    .logInfo(
                            "ETL automatic mapping="
                                    + plan.resolved.getColumns()
                                    + ", unmatchedSourceFields="
                                    + unmatched
                                    + ", excludedSourceFields="
                                    + target.getExcludedFields());
        } else {
            if (!plan.targetSchema.equals(columns))
                throw new IllegalArgumentException(
                        "ETL target schema changed during this execution");
            plan.validateRows(records);
        }
        return plan.resolved;
    }

    public static void validateRows(EtlContext context, List<DataRecord> records) {
        if (context.getTargetMappingPlan() != null)
            context.getTargetMappingPlan().validateRows(records);
    }

    public static void validateMappedFields(
            EtlTaskConfig.TargetConfig target, List<DataRecord> records) {
        for (DataRecord record : records) {
            for (String field : target.getColumns().values()) {
                if (!record.contains(field)) {
                    throw new IllegalArgumentException(
                            "ETL mapped source field is missing: " + field);
                }
            }
        }
    }

    private void validateRows(List<DataRecord> records) {
        for (DataRecord record : records)
            if (!sourceFields.equals(record.getFields().keySet()))
                throw new IllegalArgumentException(
                        "ETL transformed source fields changed during this execution");
    }

    private static TargetMappingPlan create(
            EtlTaskConfig.TargetConfig target, List<Column> schema, DataRecord first) {
        List<String> fields = new ArrayList<>(first.getFields().keySet());
        if (fields.isEmpty() || fields.stream().anyMatch(f -> f == null || f.isBlank()))
            throw new IllegalArgumentException(
                    "ETL automatic mapping requires named source fields");
        boolean database = target.getType() == EtlTaskConfig.TargetType.DATABASE;
        List<String> targets = schema.stream().map(Column::name).toList();
        Set<String> generated = new LinkedHashSet<>();
        schema.stream().filter(Column::generated).map(Column::name).forEach(generated::add);
        if (target.getMode() == EtlTaskConfig.LoadMode.SCD2 && database) {
            var scd = target.getScd2();
            generated.add(requiredMatch(scd.getValidFromColumn(), targets));
            generated.add(requiredMatch(scd.getValidToColumn(), targets));
            if (scd.getCurrentFlagColumn() != null)
                generated.add(requiredMatch(scd.getCurrentFlagColumn(), targets));
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        for (var entry : target.getColumns().entrySet()) {
            String name = database ? requiredMatch(entry.getKey(), targets) : entry.getKey();
            String source = entry.getValue();
            if (!fields.contains(source))
                throw new IllegalArgumentException(
                        "ETL override source field is missing: " + source);
            if (target.getExcludedFields().contains(source) || generated.contains(name))
                throw new IllegalArgumentException(
                        "ETL override conflicts with excluded/generated field: " + name);
            if (overrides.putIfAbsent(name, source) != null)
                throw new IllegalArgumentException("ETL duplicate override target: " + name);
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        if (database) {
            Map<String, String> automaticColumns = new LinkedHashMap<>();
            for (String field : fields) {
                if (target.getExcludedFields().contains(field) || overrides.containsValue(field))
                    continue;
                String name = match(field, targets);
                if (name == null || generated.contains(name) || overrides.containsKey(name))
                    continue;
                String previous = automaticColumns.get(name);
                if (previous != null && !previous.equals(name) && !field.equals(name))
                    throw new IllegalArgumentException(
                            "ETL ambiguous source fields for target: " + name);
                if (previous == null || field.equals(name)) automaticColumns.put(name, field);
            }
            for (String name : targets) {
                String source = overrides.getOrDefault(name, automaticColumns.get(name));
                if (source != null) mapping.put(name, source);
            }
        } else {
            for (String field : fields) {
                if (!target.getExcludedFields().contains(field)
                        && !overrides.containsValue(field)
                        && !overrides.containsKey(field)) mapping.put(field, field);
            }
            mapping.putAll(overrides);
        }
        if (mapping.isEmpty())
            throw new IllegalArgumentException("ETL automatic mapping matched no writable columns");
        EtlTaskConfig.TargetConfig effective = new EtlTaskConfig.TargetConfig();
        BeanUtils.copyProperties(target, effective);
        effective.setColumns(mapping);
        effective.setMappingMode(EtlTaskConfig.MappingMode.MANUAL);
        effective.setExcludedFields(List.of());
        if (database) {
            effective.setLogicalKeys(
                    target.getLogicalKeys().stream().map(k -> requiredMatch(k, targets)).toList());
            Map<String, EtlTaskConfig.NullPolicy> policies = new LinkedHashMap<>();
            target.getColumnNullPolicies()
                    .forEach(
                            (key, policy) -> {
                                if (policies.putIfAbsent(requiredMatch(key, targets), policy)
                                        != null)
                                    throw new IllegalArgumentException(
                                            "ETL duplicate column null policy: " + key);
                            });
            effective.setColumnNullPolicies(policies);
            if (target.getProtection() != null) {
                var protection = new EtlTaskConfig.ProtectionConfig();
                BeanUtils.copyProperties(target.getProtection(), protection);
                protection.setColumn(requiredMatch(protection.getColumn(), targets));
                effective.setProtection(protection);
            }
            if (target.getMode() == EtlTaskConfig.LoadMode.SCD2) {
                var scd = new EtlTaskConfig.SCD2Config();
                BeanUtils.copyProperties(target.getScd2(), scd);
                scd.setBusinessKeys(
                        scd.getBusinessKeys().stream()
                                .map(k -> requiredMatch(k, targets))
                                .toList());
                scd.setCompareFields(
                        scd.getCompareFields().stream()
                                .map(k -> requiredMatch(k, targets))
                                .toList());
                scd.setValidFromColumn(requiredMatch(scd.getValidFromColumn(), targets));
                scd.setValidToColumn(requiredMatch(scd.getValidToColumn(), targets));
                if (scd.getCurrentFlagColumn() != null)
                    scd.setCurrentFlagColumn(requiredMatch(scd.getCurrentFlagColumn(), targets));
                effective.setScd2(scd);
            }
            if (target.getMode() != EtlTaskConfig.LoadMode.UPDATE_ONLY)
                for (Column column : schema)
                    if (column.required()
                            && !generated.contains(column.name())
                            && !mapping.containsKey(column.name()))
                        throw new IllegalArgumentException(
                                "ETL required target column is not mapped: " + column.name());
        }
        new EtlTaskConfigValidator().validateTarget(effective);
        return new TargetMappingPlan(first.getFields().keySet(), schema, effective);
    }

    private static String requiredMatch(String name, List<String> names) {
        String result = match(name, names);
        if (result == null)
            throw new IllegalArgumentException("ETL target column does not exist: " + name);
        return result;
    }

    private static String match(String name, List<String> names) {
        if (names.contains(name)) return name;
        List<String> matches = names.stream().filter(n -> n.equalsIgnoreCase(name)).toList();
        if (matches.size() > 1)
            throw new IllegalArgumentException("ETL ambiguous column name: " + name);
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
