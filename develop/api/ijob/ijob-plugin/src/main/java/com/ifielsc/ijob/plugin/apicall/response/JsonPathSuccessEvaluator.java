package com.ifielsc.ijob.plugin.apicall.response;

import cn.hutool.core.util.StrUtil;

import com.ifielsc.ijob.core.resource.factory.service.ServiceResponse;
import com.ifielsc.ijob.plugin.apicall.SuccessCondition;
import com.jayway.jsonpath.JsonPath;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class JsonPathSuccessEvaluator implements SuccessEvaluator {
    @Override
    public boolean evaluate(ServiceResponse response, SuccessCondition condition) {
        if (condition == null) {
            return true;
        }

        if (StrUtil.isBlank(response.getBody())) {
            return false;
        }

        Object actual;
        try {
            actual = JsonPath.read(response.getBody(), condition.getExpression());
        } catch (Exception e) {
            return false;
        }
        return compare(actual, condition);
    }

    private boolean compare(Object actual, SuccessCondition condition) {
        String value = String.valueOf(actual);
        return switch (condition.getOperator().toUpperCase(Locale.ROOT)) {
            case "EQ" -> value.equals(condition.getExpected());
            case "NE" -> !value.equals(condition.getExpected());
            case "GT" -> numericCompare(value, condition.getExpected()) > 0;
            case "LT" -> numericCompare(value, condition.getExpected()) < 0;
            default -> false;
        };
    }

    private int numericCompare(String actual, String expected) {
        try {
            return new BigDecimal(actual).compareTo(new BigDecimal(expected));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
