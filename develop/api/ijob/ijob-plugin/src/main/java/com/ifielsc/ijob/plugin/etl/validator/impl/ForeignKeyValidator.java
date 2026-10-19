package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.model.EtlContext;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ForeignKeyValidator extends BatchExistValidator {
    private final SqlBatchExecutor executor;

    @Override
    public String type() {
        return ValidatorType.FOREIGN_KEY.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        return validateExist(records, config);
    }

    @Override
    public List<ValidationResult> validate(
            List<DataRecord> records, ValidatorConfig config, EtlContext context) {
        return validateExist(records, config, context);
    }

    @Override
    protected Set<Object> queryExists(Set<Object> keys, ValidatorConfig config) {
        String sql = getString(config, "sql");

        return new HashSet<>(
                executor.queryIn(
                        getLong(config, "resourceId"),
                        sql,
                        keys,
                        getInt(config, "queryTimeoutSeconds", 300)));
    }

    @Override
    protected Set<Object> queryExists(
            Set<Object> keys, ValidatorConfig config, EtlContext context) {
        return new HashSet<>(
                executor.queryIn(
                        getLong(config, "resourceId"),
                        getString(config, "sql"),
                        keys,
                        getInt(config, "queryTimeoutSeconds", 300),
                        getInt(config, "batchSize", 500),
                        context));
    }
}
