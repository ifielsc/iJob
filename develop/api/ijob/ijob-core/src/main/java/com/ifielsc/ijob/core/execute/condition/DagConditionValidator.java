package com.ifielsc.ijob.core.execute.condition;

import com.ifielsc.ijob.common.exception.BusinessException;
import com.ifielsc.ijob.common.response.ErrorDescriptor;
import com.ifielsc.ijob.common.response.ResultCode;
import com.ifielsc.ijob.contract.job.DagConditionExpression;
import com.ifielsc.ijob.contract.job.DagConditionOperand;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Component
public class DagConditionValidator {
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODE_COUNT = 100;
    public static final int MAX_IN_VALUES = 100;
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile(
                    "(?:input\\.[A-Za-z0-9_.-]+|nodes\\.[A-Za-z0-9_.-]+|trigger\\.(?:id|type|time|date))");

    public void validate(DagConditionExpression expression) {
        AtomicInteger count = new AtomicInteger();
        validateExpression(expression, 1, count);
    }

    public Set<String> variables(DagConditionExpression expression) {
        validate(expression);
        Set<String> result = new LinkedHashSet<>();
        collectVariables(expression, result);
        return Set.copyOf(result);
    }

    private void collectVariables(DagConditionExpression expression, Set<String> result) {
        if (expression.getLeft() != null && expression.getLeft().getVar() != null) {
            result.add(expression.getLeft().getVar());
        }
        if (expression.getRight() != null && expression.getRight().getVar() != null) {
            result.add(expression.getRight().getVar());
        }
        if (expression.getOperand() != null) {
            collectVariables(expression.getOperand(), result);
        }
        if (expression.getOperands() != null) {
            expression.getOperands().forEach(operand -> collectVariables(operand, result));
        }
    }

    private void validateExpression(
            DagConditionExpression expression, int depth, AtomicInteger count) {
        if (expression == null || expression.getOp() == null) {
            throw invalid("Condition operator is required");
        }
        if (depth > MAX_DEPTH || count.incrementAndGet() > MAX_NODE_COUNT) {
            throw invalid("Condition complexity limit exceeded");
        }
        switch (expression.getOp()) {
            case AND, OR -> validateBooleanGroup(expression, depth, count);
            case NOT -> validateNot(expression, depth, count);
            case EQ, NE, GT, GE, LT, LE -> validateComparison(expression);
            case IN, NOT_IN -> validateMembership(expression);
            case EXISTS, IS_NULL, IS_NOT_NULL -> validateUnaryValue(expression);
        }
    }

    private void validateBooleanGroup(
            DagConditionExpression expression, int depth, AtomicInteger count) {
        List<DagConditionExpression> operands = expression.getOperands();
        if (operands == null || operands.size() < 2 || hasValueFields(expression)) {
            throw invalid(expression.getOp() + " requires at least two condition operands");
        }
        operands.forEach(operand -> validateExpression(operand, depth + 1, count));
    }

    private void validateNot(DagConditionExpression expression, int depth, AtomicInteger count) {
        if (expression.getOperand() == null
                || expression.getOperands() != null
                || expression.getLeft() != null
                || expression.getRight() != null) {
            throw invalid("NOT requires exactly one condition operand");
        }
        validateExpression(expression.getOperand(), depth + 1, count);
    }

    private void validateComparison(DagConditionExpression expression) {
        requireBinaryValues(expression);
        validateValue(expression.getLeft(), false);
        validateValue(expression.getRight(), false);
    }

    private void validateMembership(DagConditionExpression expression) {
        requireBinaryValues(expression);
        validateValue(expression.getLeft(), false);
        DagConditionOperand right = expression.getRight();
        if (right.getVar() != null || !(right.getLiteral() instanceof Collection<?> values)) {
            throw invalid(expression.getOp() + " requires a literal array on the right");
        }
        if (values.isEmpty() || values.size() > MAX_IN_VALUES) {
            throw invalid("IN literal array must contain 1 to 100 values");
        }
        values.forEach(this::validateLiteral);
    }

    private void validateUnaryValue(DagConditionExpression expression) {
        if (expression.getLeft() == null
                || expression.getRight() != null
                || expression.getOperands() != null
                || expression.getOperand() != null) {
            throw invalid(expression.getOp() + " requires exactly one variable operand");
        }
        validateValue(expression.getLeft(), true);
    }

    private void requireBinaryValues(DagConditionExpression expression) {
        if (expression.getLeft() == null
                || expression.getRight() == null
                || expression.getOperands() != null
                || expression.getOperand() != null) {
            throw invalid(expression.getOp() + " requires left and right operands");
        }
    }

    private void validateValue(DagConditionOperand operand, boolean requireVariable) {
        String variable = operand.getVar();
        if (variable != null) {
            if (operand.getLiteral() != null || !VARIABLE_PATTERN.matcher(variable).matches()) {
                throw invalid("Condition variable is invalid: " + variable);
            }
            return;
        }
        if (requireVariable) {
            throw invalid("Condition operand must reference a variable");
        }
        validateLiteral(operand.getLiteral());
    }

    private void validateLiteral(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Boolean
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double) {
            return;
        }
        throw invalid("Condition literal type is not supported");
    }

    private boolean hasValueFields(DagConditionExpression expression) {
        return expression.getLeft() != null
                || expression.getRight() != null
                || expression.getOperand() != null;
    }

    private BusinessException invalid(String message) {
        String key = null;
        String safe = message;
        if (message.endsWith(" requires at least two condition operands")) {
            key = "group";
            safe = "AND/OR require at least two conditions";
        } else if (message.endsWith(" requires a literal array on the right")) {
            key = "array";
            safe = "IN/NOT_IN require a literal array on the right";
        } else if (message.endsWith(" requires exactly one variable operand")) {
            key = "unary";
            safe = "Unary conditions require exactly one variable operand";
        } else if (message.endsWith(" requires left and right operands")) {
            key = "binary";
            safe = "Comparison conditions require left and right operands";
        } else if (message.startsWith("Condition variable is invalid:")) {
            key = "variable";
            safe = "Condition variable path is invalid";
        }
        if (key != null)
            return new BusinessException(
                    new ErrorDescriptor(
                            ResultCode.PARAM_ERROR,
                            "IJOB-JOB-CONDITION-" + key.toUpperCase(Locale.ROOT),
                            "error.job.condition." + key,
                            safe));
        return BusinessException.localized(ResultCode.PARAM_ERROR, message);
    }
}
