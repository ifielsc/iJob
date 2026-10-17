package com.ifelio.ijob.plugin.etl.transformer.impl;

import com.ifelio.ijob.plugin.etl.model.DataRecord;
import com.ifelio.ijob.plugin.etl.transformer.AbstractTransformer;
import com.ifelio.ijob.plugin.etl.transformer.TransformConfig;
import com.ifelio.ijob.plugin.etl.transformer.TransformType;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExpressionTransformer extends AbstractTransformer {
    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public String type() {
        return TransformType.EXPRESSION.name();
    }

    @Override
    public List<DataRecord> transform(List<DataRecord> records, TransformConfig config) {
        String target = getString(config, "target");

        String expression = getString(config, "expression");

        for (DataRecord record : records) {
            SimpleEvaluationContext context =
                    SimpleEvaluationContext.forReadOnlyDataBinding()
                            .withRootObject(record.getFields())
                            .build();

            Object value = parser.parseExpression(expression).getValue(context);

            record.put(target, value);
        }

        return records;
    }
}
