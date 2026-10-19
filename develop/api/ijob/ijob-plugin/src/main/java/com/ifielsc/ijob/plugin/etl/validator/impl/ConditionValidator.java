package com.ifielsc.ijob.plugin.etl.validator.impl;

import com.ifielsc.ijob.plugin.etl.model.DataRecord;
import com.ifielsc.ijob.plugin.etl.validator.AbstractValidator;
import com.ifielsc.ijob.plugin.etl.validator.ValidationResult;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorConfig;
import com.ifielsc.ijob.plugin.etl.validator.ValidatorType;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConditionValidator extends AbstractValidator {
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public String type() {
        return ValidatorType.CONDITION.name();
    }

    @Override
    public List<ValidationResult> validate(List<DataRecord> records, ValidatorConfig config) {
        Expression expression = parser.parseExpression(getString(config, "expression"));
        List<ValidationResult> result = new ArrayList<>();
        for (DataRecord record : records) {
            SimpleEvaluationContext context =
                    SimpleEvaluationContext.forReadOnlyDataBinding()
                            .withRootObject(record.getFields())
                            .build();
            Boolean valid = expression.getValue(context, Boolean.class);
            result.add(
                    Boolean.TRUE.equals(valid)
                            ? ValidationResult.success(record)
                            : ValidationResult.fail(record, null, "condition validation failed"));
        }
        return result;
    }
}
