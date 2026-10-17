package com.ifelio.ijob.plugin.apicall.response;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ifelio.ijob.common.exception.BusinessException;
import com.ifelio.ijob.core.execute.ExecutionHandle;
import com.ifelio.ijob.core.execute.task.TaskContext;
import com.ifelio.ijob.core.execute.variable.VariableResolver;
import com.ifelio.ijob.core.resource.factory.service.ServiceResponse;
import com.ifelio.ijob.plugin.apicall.ResponseExtractor;
import com.ifelio.ijob.plugin.apicall.SuccessCondition;
import com.ifelio.ijob.plugin.support.RecordingInstanceLogs;

import org.junit.jupiter.api.Test;

import java.util.List;

class JsonPathResponseProcessingTest {
    @Test
    void evaluatorShouldSupportStringAndNumericOperators() {
        JsonPathSuccessEvaluator evaluator = new JsonPathSuccessEvaluator();
        ServiceResponse response = response("{\"code\":\"OK\",\"score\":10.5}");

        assertAll(
                () -> assertTrue(evaluator.evaluate(response, condition("$.code", "EQ", "OK"))),
                () -> assertTrue(evaluator.evaluate(response, condition("$.code", "ne", "FAILED"))),
                () -> assertTrue(evaluator.evaluate(response, condition("$.score", "GT", "10.4"))),
                () -> assertTrue(evaluator.evaluate(response, condition("$.score", "LT", "11"))),
                () -> assertFalse(evaluator.evaluate(response, condition("$.code", "GT", "1"))));
    }

    @Test
    void extractorShouldApplyValuesTogetherAndClearJsonNull() {
        JsonPathResponseExtractorService extractor = new JsonPathResponseExtractorService();
        TaskContext context = context();
        context.putVariable("optional", "old");

        extractor.extract(
                "{\"data\":{\"id\":7,\"optional\":null}}",
                List.of(extractor("id", "$.data.id"), extractor("optional", "$.data.optional")),
                context);

        assertTrue(context.containsVariable("id"));
        assertFalse(context.containsVariable("optional"));
    }

    @Test
    void extractorFailureShouldNotPublishEarlierValues() {
        JsonPathResponseExtractorService extractor = new JsonPathResponseExtractorService();
        TaskContext context = context();

        assertThrows(
                BusinessException.class,
                () ->
                        extractor.extract(
                                "{\"data\":{\"id\":7}}",
                                List.of(
                                        extractor("id", "$.data.id"),
                                        extractor("missing", "$.data.missing")),
                                context));
        assertFalse(context.containsVariable("id"));
    }

    @Test
    void configuredExtractorShouldRejectEmptyBody() {
        JsonPathResponseExtractorService extractor = new JsonPathResponseExtractorService();

        assertThrows(
                BusinessException.class,
                () -> extractor.extract(" ", List.of(extractor("id", "$.id")), context()));
    }

    private TaskContext context() {
        return new TaskContext(
                1L,
                new ExecutionHandle(1L),
                null,
                new RecordingInstanceLogs(),
                new VariableResolver());
    }

    private ServiceResponse response(String body) {
        ServiceResponse response = new ServiceResponse();
        response.setStatus(200);
        response.setBody(body);
        return response;
    }

    private SuccessCondition condition(String expression, String operator, String expected) {
        SuccessCondition condition = new SuccessCondition();
        condition.setExpression(expression);
        condition.setOperator(operator);
        condition.setExpected(expected);
        return condition;
    }

    private ResponseExtractor extractor(String variable, String expression) {
        ResponseExtractor extractor = new ResponseExtractor();
        extractor.setVariable(variable);
        extractor.setExpression(expression);
        return extractor;
    }
}
