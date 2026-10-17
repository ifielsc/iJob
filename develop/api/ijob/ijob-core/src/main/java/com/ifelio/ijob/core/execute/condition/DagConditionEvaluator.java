package com.ifelio.ijob.core.execute.condition;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.common.response.ResultCode;
import com.ifelio.ijob.contract.job.DagConditionExpression;
import com.ifelio.ijob.contract.job.DagConditionOperand;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DagConditionEvaluator {
    public static final int EVALUATOR_VERSION = 1;

    private final DagConditionValidator validator;

    public boolean evaluate(DagConditionExpression expression, Map<String, Object> context) {
        validator.validate(expression);
        if (context == null) {
            throw evaluationError("Gateway condition context is missing");
        }
        return evaluateExpression(expression, context);
    }

    private boolean evaluateExpression(
            DagConditionExpression expression, Map<String, Object> context) {
        return switch (expression.getOp()) {
            case AND ->
                    expression.getOperands().stream()
                            .allMatch(operand -> evaluateExpression(operand, context));
            case OR ->
                    expression.getOperands().stream()
                            .anyMatch(operand -> evaluateExpression(operand, context));
            case NOT -> !evaluateExpression(expression.getOperand(), context);
            case EQ ->
                    equal(
                            value(expression.getLeft(), context, true),
                            value(expression.getRight(), context, true));
            case NE ->
                    !equal(
                            value(expression.getLeft(), context, true),
                            value(expression.getRight(), context, true));
            case GT -> compare(expression, context) > 0;
            case GE -> compare(expression, context) >= 0;
            case LT -> compare(expression, context) < 0;
            case LE -> compare(expression, context) <= 0;
            case IN -> membership(expression, context);
            case NOT_IN -> !membership(expression, context);
            case EXISTS -> lookup(expression.getLeft().getVar(), context).present();
            case IS_NULL -> value(expression.getLeft(), context, true) == null;
            case IS_NOT_NULL -> value(expression.getLeft(), context, true) != null;
        };
    }

    private int compare(DagConditionExpression expression, Map<String, Object> context) {
        Object left = value(expression.getLeft(), context, true);
        Object right = value(expression.getRight(), context, true);
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return decimal(leftNumber).compareTo(decimal(rightNumber));
        }
        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareTo(rightString);
        }
        throw evaluationError("Gateway condition values are not comparable");
    }

    private boolean membership(DagConditionExpression expression, Map<String, Object> context) {
        Object candidate = value(expression.getLeft(), context, true);
        Object literal = expression.getRight().getLiteral();
        if (!(literal instanceof Collection<?> values)) {
            throw evaluationError("Gateway condition membership list is invalid");
        }
        return values.stream().anyMatch(value -> equal(candidate, value));
    }

    private boolean equal(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return decimal(leftNumber).compareTo(decimal(rightNumber)) == 0;
        }
        if (left == null || right == null) {
            return left == right;
        }
        if (!left.getClass().equals(right.getClass())) {
            throw evaluationError("Gateway condition value types do not match");
        }
        return Objects.equals(left, right);
    }

    private Object value(
            DagConditionOperand operand, Map<String, Object> context, boolean requirePresent) {
        if (operand.getVar() == null) {
            return operand.getLiteral();
        }
        Lookup lookup = lookup(operand.getVar(), context);
        if (requirePresent && !lookup.present()) {
            throw evaluationError("Gateway condition variable is missing: " + operand.getVar());
        }
        return lookup.value();
    }

    private Lookup lookup(String path, Map<String, Object> context) {
        if (path.startsWith("nodes.")) {
            return lookupNodeOutput(path.substring("nodes.".length()), context.get("nodes"));
        }
        return lookupSegments(path, context);
    }

    private Lookup lookupNodeOutput(String path, Object nodeOutputs) {
        if (!(nodeOutputs instanceof Map<?, ?> nodes)) {
            return new Lookup(false, null);
        }
        String matchedNodeKey = null;
        for (Object key : nodes.keySet()) {
            if (key instanceof String nodeKey && path.startsWith(nodeKey + ".")) {
                if (matchedNodeKey != null) {
                    throw evaluationError("Gateway condition node reference is ambiguous");
                }
                matchedNodeKey = nodeKey;
            }
        }
        if (matchedNodeKey == null) {
            return new Lookup(false, null);
        }
        return lookupSegments(
                path.substring(matchedNodeKey.length() + 1), nodes.get(matchedNodeKey));
    }

    private Lookup lookupSegments(String path, Object root) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return new Lookup(false, null);
            }
            current = map.get(segment);
        }
        return new Lookup(true, current);
    }

    private BigDecimal decimal(Number value) {
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            throw evaluationError("Gateway condition numeric value is invalid");
        }
    }

    private BusinessException evaluationError(String message) {
        return BusinessException.localized(ResultCode.SYSTEM_ERROR, message);
    }

    private record Lookup(boolean present, Object value) {}
}
