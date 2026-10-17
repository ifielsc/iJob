package com.ifelio.ijob.core.execute.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.contract.enums.DagConditionOperator;
import com.ifelio.ijob.contract.job.DagConditionExpression;
import com.ifelio.ijob.contract.job.DagConditionOperand;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DagConditionEvaluatorTest {
    private final DagConditionValidator validator = new DagConditionValidator();
    private final DagConditionEvaluator evaluator = new DagConditionEvaluator(validator);

    @Test
    void shouldEvaluateTypedBooleanTreeAndNormalizeNumbers() {
        DagConditionExpression expression =
                group(
                        DagConditionOperator.AND,
                        compare(DagConditionOperator.GE, variable("input.amount"), literal(10)),
                        unary(
                                DagConditionOperator.NOT,
                                compare(
                                        DagConditionOperator.EQ,
                                        variable("trigger.type"),
                                        literal("MANUAL"))),
                        compare(
                                DagConditionOperator.IN,
                                variable("nodes.prepare.region"),
                                literal(List.of("cn-east", "cn-north"))));

        Map<String, Object> context =
                Map.of(
                        "input", Map.of("amount", new BigDecimal("10.00")),
                        "nodes", Map.of("prepare", Map.of("region", "cn-east")),
                        "trigger", Map.of("type", "SCHEDULE"));

        assertTrue(evaluator.evaluate(expression, context));
    }

    @Test
    void shouldDistinguishMissingFromPresentNull() {
        Map<String, Object> input = new HashMap<>();
        input.put("nullable", null);
        Map<String, Object> context = Map.of("input", input);

        assertTrue(
                evaluator.evaluate(
                        compare(DagConditionOperator.IS_NULL, variable("input.nullable"), null),
                        context));
        assertFalse(
                evaluator.evaluate(
                        compare(DagConditionOperator.EXISTS, variable("input.missing"), null),
                        context));
        assertThrows(
                BusinessException.class,
                () ->
                        evaluator.evaluate(
                                compare(
                                        DagConditionOperator.EQ,
                                        variable("input.missing"),
                                        literal(1)),
                                context));
    }

    @Test
    void shouldFailClosedForTypeMismatchAndUnsupportedVariable() {
        assertThrows(
                BusinessException.class,
                () ->
                        evaluator.evaluate(
                                compare(
                                        DagConditionOperator.EQ,
                                        variable("input.value"),
                                        literal("1")),
                                Map.of("input", Map.of("value", 1))));
        assertThrows(
                BusinessException.class,
                () ->
                        validator.validate(
                                compare(
                                        DagConditionOperator.EQ,
                                        variable("system.secret"),
                                        literal(1))));
    }

    @Test
    void shouldResolveDirectUpstreamNodeKeysContainingDots() {
        DagConditionExpression expression =
                compare(
                        DagConditionOperator.EQ,
                        variable("nodes.prepare.v1.region"),
                        literal("cn-east"));

        assertTrue(
                evaluator.evaluate(
                        expression,
                        Map.of("nodes", Map.of("prepare.v1", Map.of("region", "cn-east")))));
    }

    @Test
    void shouldEnforceDepthNodeAndMembershipLimits() {
        DagConditionExpression nested =
                compare(DagConditionOperator.EQ, variable("input.value"), literal(1));
        for (int index = 0; index < DagConditionValidator.MAX_DEPTH; index++) {
            nested = unary(DagConditionOperator.NOT, nested);
        }
        DagConditionExpression tooDeep = nested;

        assertThrows(BusinessException.class, () -> validator.validate(tooDeep));
        assertThrows(
                BusinessException.class,
                () ->
                        validator.validate(
                                compare(
                                        DagConditionOperator.IN,
                                        variable("input.value"),
                                        literal(
                                                new ArrayList<>(
                                                        java.util.Collections.nCopies(
                                                                DagConditionValidator.MAX_IN_VALUES
                                                                        + 1,
                                                                1))))));
    }

    private static DagConditionExpression group(
            DagConditionOperator operator, DagConditionExpression... operands) {
        DagConditionExpression expression = new DagConditionExpression();
        expression.setOp(operator);
        expression.setOperands(List.of(operands));
        return expression;
    }

    private static DagConditionExpression unary(
            DagConditionOperator operator, DagConditionExpression operand) {
        DagConditionExpression expression = new DagConditionExpression();
        expression.setOp(operator);
        expression.setOperand(operand);
        return expression;
    }

    private static DagConditionExpression compare(
            DagConditionOperator operator, DagConditionOperand left, DagConditionOperand right) {
        DagConditionExpression expression = new DagConditionExpression();
        expression.setOp(operator);
        expression.setLeft(left);
        expression.setRight(right);
        return expression;
    }

    private static DagConditionOperand variable(String path) {
        DagConditionOperand operand = new DagConditionOperand();
        operand.setVar(path);
        return operand;
    }

    private static DagConditionOperand literal(Object value) {
        DagConditionOperand operand = new DagConditionOperand();
        operand.setLiteral(value);
        return operand;
    }
}
